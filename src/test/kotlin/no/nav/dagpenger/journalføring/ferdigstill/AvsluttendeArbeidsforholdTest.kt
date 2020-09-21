package no.nav.dagpenger.journalføring.ferdigstill

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.events.Packet
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

internal class ArbeidsforholdTest {

    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    @Test
    fun `Skal kunne hente årsak til avsluttet arbeidsforhold`() {
        val data = objectMapper.readValue(søknadWithArbeidsforhold(), Map::class.java)

        val packet = Packet().apply {
            this.putValue("søknadsdata", data)
        }

        packet.harAvsluttetArbeidsforholdFraKonkurs() shouldBe true
    }

    @Test
    fun `Skal kunne hente om søker er grensearbeider`() {
        val data = objectMapper.readValue(søknadWithArbeidsforhold(), Map::class.java)

        val packet = Packet().apply {
            this.putValue("søknadsdata", data)
        }

        packet.erGrenseArbeider() shouldBe true
    }
}

@Language("JSON")
private fun søknadWithArbeidsforhold(
    lonnspliktigperiodedatofra: String? = "2020-03-19",
    lonnspliktigperiodedatotil: String? = "2020-03-20"
) =
    """{
  "fakta": [
    {
      "faktumId": 776466,
      "soknadId": 10636,
      "parrentFaktum": null,
      "key": "arbeidsforhold.datodagpenger",
      "value": "2020-03-19",
      "faktumEgenskaper": [],
      "properties": {},
      "type": "BRUKERREGISTRERT"
    },
    {
      "faktumId": 776467,
      "soknadId": 10636,
      "parrentFaktum": null,
      "key": "arbeidsforhold.arbeidstilstand",
      "value": "fastArbeidstid",
      "faktumEgenskaper": [],
      "properties": {},
      "type": "BRUKERREGISTRERT"
    },
    {
      "faktumId": 776468,
      "soknadId": 10636,
      "parrentFaktum": null,
      "key": "arbeidsforhold.grensearbeider",
      "value": "false",
      "faktumEgenskaper": [],
      "properties": {},
      "type": "BRUKERREGISTRERT"
    },
    {
      "faktumId": 776566,
      "soknadId": 10636,
      "parrentFaktum": null,
      "key": "arbeidsforhold",
      "value": null,
      "faktumEgenskaper": [
        {
          "faktumId": 776566,
          "soknadId": 10636,
          "key": "datofra",
          "value": "2000-01-01",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776566,
          "soknadId": 10636,
          "key": "type",
          "value": "arbeidsgivererkonkurs",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776566,
          "soknadId": 10636,
          "key": "rotasjonskiftturnus",
          "value": "nei",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776566,
          "soknadId": 10636,
          "key": "lonnspliktigperiodedatofra",
          "value": "2020-03-19",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776566,
          "soknadId": 10636,
          "key": "land",
          "value": "NOR",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776566,
          "soknadId": 10636,
          "key": "skalHaT8VedleggForKontraktUtgaatt",
          "value": "false",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776566,
          "soknadId": 10636,
          "key": "eosland",
          "value": "false",
          "systemEgenskap": 0
        },
        {
          "key":"laerling",
          "value":"true",
          "faktumId":798841,
          "soknadId":10636,
          "systemEgenskap":0
        },
        {
          "key":"fangstogfisk",
          "value":"true",
          "faktumId":776566,
          "soknadId":10636,
          "systemEgenskap":0
        },
        {
          "faktumId": 776566,
          "soknadId": 10636,
          "key": "arbeidsgivernavn",
          "value": "Rakettforskeren",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776566,
          "soknadId": 10636,
          "key": "lonnspliktigperiodedatotil",
          "value": "2020-03-20",
          "systemEgenskap": 0
        }
      ],
      "properties": {
        "arbeidsgivernavn": "Rakettforskeren",
        "eosland": "false",
        "fangstogfisk": "true",
        "laerling": "true",
        "skalHaT8VedleggForKontraktUtgaatt": "false",
        "datofra": "2000-01-01",
        "lonnspliktigperiodedatofra": "$lonnspliktigperiodedatofra",
        "lonnspliktigperiodedatotil": "$lonnspliktigperiodedatotil",
        "rotasjonskiftturnus": "nei",
        "land": "NOR",
        "type": "arbeidsgivererkonkurs"
      },
      "type": "BRUKERREGISTRERT"
    },
    {
      "faktumId": 7765661,
      "soknadId": 10636,
      "parrentFaktum": null,
      "key": "arbeidsforhold",
      "value": null,
      "faktumEgenskaper": [
        {
          "faktumId": 7765661,
          "soknadId": 10636,
          "key": "datofra",
          "value": "2000-01-01",
          "systemEgenskap": 0
        },
        {
          "faktumId": 7765661,
          "soknadId": 10636,
          "key": "type",
          "value": "permittert",
          "systemEgenskap": 0
        },
        {
          "faktumId": 7765661,
          "soknadId": 10636,
          "key": "rotasjonskiftturnus",
          "value": "nei",
          "systemEgenskap": 0
        },
        {
          "faktumId": 7765661,
          "soknadId": 10636,
          "key": "lonnspliktigperiodedatofra",
          "value": "2020-03-19",
          "systemEgenskap": 0
        },
        {
          "faktumId": 7765661,
          "soknadId": 10636,
          "key": "land",
          "value": "NOR",
          "systemEgenskap": 0
        },
        {
          "faktumId": 7765661,
          "soknadId": 10636,
          "key": "skalHaT8VedleggForKontraktUtgaatt",
          "value": "false",
          "systemEgenskap": 0
        },
        {
          "faktumId": 7765661,
          "soknadId": 10636,
          "key": "eosland",
          "value": "false",
          "systemEgenskap": 0
        },
        {
          "faktumId": 7765661,
          "soknadId": 10636,
          "key": "arbeidsgivernavn",
          "value": "Rakettforskeren",
          "systemEgenskap": 0
        },
        {
          "faktumId": 7765661,
          "soknadId": 10636,
          "key": "lonnspliktigperiodedatotil",
          "value": "2020-03-20",
          "systemEgenskap": 0
        }
      ],
      "properties": {
        "arbeidsgivernavn": "Deltidsjobben",
        "eosland": "false",
        "skalHaT8VedleggForKontraktUtgaatt": "false",
        "datofra": "2000-01-01",
        "lonnspliktigperiodedatofra": "$lonnspliktigperiodedatofra",
        "lonnspliktigperiodedatotil": "$lonnspliktigperiodedatotil",
        "rotasjonskiftturnus": "ja",
        "land": "SWE",
        "type": "sagtoppavarbeidsgiver"
      },
      "type": "BRUKERREGISTRERT"
    },
    {
      "faktumId": 776567,
      "soknadId": 10636,
      "parrentFaktum": 776566,
      "key": "arbeidsforhold.permitteringsperiode",
      "value": null,
      "faktumEgenskaper": [
        {
          "faktumId": 776567,
          "soknadId": 10636,
          "key": "permitteringProsent",
          "value": "100",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776567,
          "soknadId": 10636,
          "key": "permiteringsperiodedatofra",
          "value": "2020-03-23",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776567,
          "soknadId": 10636,
          "key": "permitteringsperiodeTittel",
          "value": "Rakettforskeren (23. mars 2020 - pågående)",
          "systemEgenskap": 0
        }
      ],
      "properties": {
        "permiteringsperiodedatofra": "2020-03-23",
        "permitteringsperiodeTittel": "Rakettforskeren (23. mars 2020 - pågående)",
        "permitteringProsent": "100"
      },
      "type": "BRUKERREGISTRERT"
    },
    {
      "faktumId": 776567,
      "soknadId": 10636,
      "parrentFaktum": 776566,
      "key": "arbeidsforhold.permitteringsperiode",
      "value": null,
      "faktumEgenskaper": [
        {
          "faktumId": 776567,
          "soknadId": 10636,
          "key": "permitteringProsent",
          "value": "100",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776567,
          "soknadId": 10636,
          "key": "permiteringsperiodedatofra",
          "value": "2020-03-23",
          "systemEgenskap": 0
        },
        {
          "faktumId": 776567,
          "soknadId": 10636,
          "key": "permitteringsperiodeTittel",
          "value": "Rakettforskeren (23. mars 2020 - pågående)",
          "systemEgenskap": 0
        }
      ],
      "properties": {
        "lonnspliktigperiodedatofra": "$lonnspliktigperiodedatofra",
        "lonnspliktigperiodedatotil": "$lonnspliktigperiodedatotil",
        "permitteringsperiodeTittel": "Rakettforskeren (23. mars 2020 - pågående)",
        "permitteringProsent": "100"
      },
      "type": "BRUKERREGISTRERT"
    }
  ]
}
    """.trimIndent()
