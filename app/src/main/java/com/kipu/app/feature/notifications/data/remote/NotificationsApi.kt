package com.kipu.app.feature.notifications.data.remote

import com.kipu.app.core.network.AuthenticatedSessionProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

@Singleton
class NotificationsApi @Inject constructor(
    private val httpClient: HttpClient,
    private val sessionProvider: AuthenticatedSessionProvider,
) {
    private val responseJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun fetchForUser(userId: String): NotificationsApiResponse<List<NotificationDto>> {
        val session = sessionProvider.currentSession()
            ?: return NotificationsApiResponse.Error(401, "No active session")
        if (session.userId.toString() != userId) return NotificationsApiResponse.Error(403, "Active session owner mismatch")

        return try {
            val response = httpClient.get("rest/v1/app_notifications") {
                header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                url {
                    parameters.append(
                        "select",
                        "id,user_id,title,body,notification_type,reference_entity_type,reference_entity_id,is_read,created_at,deleted_at,event_payload",
                    )
                    parameters.append("user_id", "eq.$userId")
                    parameters.append("order", "created_at.desc")
                }
            }
            if (response.status.value !in 200..299) {
                NotificationsApiResponse.Error(response.status.value, response.body<String>())
            } else {
                val rows = responseJson.decodeFromString(ListSerializer(NotificationDto.serializer()), response.body<String>())
                NotificationsApiResponse.Success(rows)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (serialization: SerializationException) {
            NotificationsApiResponse.Error(502, serialization.message)
        } catch (failure: Exception) {
            NotificationsApiResponse.NetworkFailure(failure)
        }
    }

    suspend fun patchForUser(
        userId: String,
        notificationId: String,
        mutation: NotificationMutationDto,
    ): NotificationsApiResponse<Unit> {
        val session = sessionProvider.currentSession()
            ?: return NotificationsApiResponse.Error(401, "No active session")
        if (session.userId.toString() != userId) return NotificationsApiResponse.Error(403, "Active session owner mismatch")

        return try {
            val response = httpClient.patch("rest/v1/app_notifications") {
                header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                contentType(ContentType.Application.Json)
                url {
                    parameters.append("id", "eq.$notificationId")
                    parameters.append("user_id", "eq.$userId")
                }
                setBody(mutation)
            }
            if (response.status.value in 200..299) {
                NotificationsApiResponse.Success(Unit)
            } else {
                NotificationsApiResponse.Error(response.status.value, response.body<String>())
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            NotificationsApiResponse.NetworkFailure(failure)
        }
    }
}
