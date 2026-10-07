package com.kipu.app.feature.categories.presentation
 
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.MerchantId
import com.kipu.app.feature.categories.presentation.components.UiMerchantGroup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
 
class UiMerchantGroupTest {
 
     @Test
     fun primaxMatchesGasAndNeverEntertainment() {
         val primax = MerchantCatalogEntry(
             id = MerchantId("00000000-0000-0000-0001-000000000023"),
             name = "Primax",
             normalizedName = "primax",
         )
         assertTrue("Primax must match GAS", UiMerchantGroup.GAS.matches(primax))
         assertFalse("Primax must NOT match ENTERTAINMENT despite containing 'max'", UiMerchantGroup.ENTERTAINMENT.matches(primax))
     }
 
     @Test
     fun metropolitanoMatchesTransportAndNeverSupermarkets() {
         val metropolitano = MerchantCatalogEntry(
             id = MerchantId("00000000-0000-0000-0001-000000000026"),
             name = "Metropolitano",
             normalizedName = "metropolitano",
         )
         assertTrue("Metropolitano must match TRANSPORT", UiMerchantGroup.TRANSPORT.matches(metropolitano))
         assertFalse("Metropolitano must NOT match SUPERMARKETS despite containing 'metro'", UiMerchantGroup.SUPERMARKETS.matches(metropolitano))
     }
 
     @Test
     fun maxAndMetroMatchTheirRespectiveGroups() {
         val max = MerchantCatalogEntry(
             id = MerchantId("00000000-0000-0000-0001-000000000046"),
             name = "Max",
             normalizedName = "max",
         )
         val metro = MerchantCatalogEntry(
             id = MerchantId("00000000-0000-0000-0001-000000000005"),
             name = "Metro",
             normalizedName = "metro",
         )
         assertTrue("Max matches ENTERTAINMENT", UiMerchantGroup.ENTERTAINMENT.matches(max))
         assertFalse("Max does NOT match GAS", UiMerchantGroup.GAS.matches(max))
 
         assertTrue("Metro matches SUPERMARKETS", UiMerchantGroup.SUPERMARKETS.matches(metro))
         assertFalse("Metro does NOT match TRANSPORT", UiMerchantGroup.TRANSPORT.matches(metro))
     }
 
     @Test
     fun accentsAndCaseVariationsAreCorrectlyClassified() {
         val calidda = MerchantCatalogEntry(
             id = MerchantId("00000000-0000-0000-0001-000000000034"),
             name = "Cálidda",
             normalizedName = "calidda",
         )
         val linea1 = MerchantCatalogEntry(
             id = MerchantId("00000000-0000-0000-0001-000000000027"),
             name = "Línea 1",
             normalizedName = "linea 1",
         )
         assertTrue("Cálidda matches UTILITIES", UiMerchantGroup.UTILITIES.matches(calidda))
         assertTrue("Línea 1 matches TRANSPORT", UiMerchantGroup.TRANSPORT.matches(linea1))
     }
 }
