package no.nav.dagpenger.journalføring.ferdigstill

import com.gregwoodfill.assert.shouldStrictlyEqualJson
import org.junit.jupiter.api.Test

internal class JoarkPayloadTest {

    @Test
    fun `Json serialiation of OppdaterJournalPostPayload with FAGSAK`() {

        val json = JournalPostRestApi.toJsonPayload(OppdaterJournalPostPayload(
            avsenderMottaker = Avsender("navn"),
            bruker = Bruker("bruker"),
            tittel = "tittel",
            sak = Sak(SaksType.FAGSAK, "fagsakId", "AO01"),
            dokumenter = listOf(Dokument("dokumentId", "tittel"))
        ))

        json shouldStrictlyEqualJson """
            {
              "avsenderMottaker": {
                "navn": "navn"
              },
              "bruker": {
                "id": "bruker",
                "idType": "FNR"
              },
              "tittel": "tittel",
              "sak": {
                "saksType": "FAGSAK",
                "fagsakId": "fagsakId",
                "fagsaksystem": "AO01"
              },
              "dokumenter": [
                {
                  "dokumentinfoId": "dokumentId",
                  "tittel": "tittel"
                }
              ],
              "behandlingstema": "ab0001",
              "tema": "DAG",
              "journalfoerendeEnhet": "9999"
            }
        """
    }

    @Test
    fun `Json serialiation of OppdaterJournalPostPayload with GENERELL_SAK`() {

        val json = JournalPostRestApi.toJsonPayload(OppdaterJournalPostPayload(
            avsenderMottaker = Avsender("navn"),
            bruker = Bruker("bruker"),
            tittel = "tittel",
            sak = Sak(SaksType.GENERELL_SAK, null, null),
            dokumenter = listOf(Dokument("dokumentId", "tittel"))
        ))

        json shouldStrictlyEqualJson """
            {
              "avsenderMottaker": {
                "navn": "navn"
              },
              "bruker": {
                "id": "bruker",
                "idType": "FNR"
              },
              "tittel": "tittel",
              "sak": {
                "saksType": "GENERELL_SAK"
              },
              "dokumenter": [
                {
                  "dokumentinfoId": "dokumentId",
                  "tittel": "tittel"
                }
              ],
              "behandlingstema": "ab0001",
              "tema": "DAG",
              "journalfoerendeEnhet": "9999"
            }
        """
    }
}
