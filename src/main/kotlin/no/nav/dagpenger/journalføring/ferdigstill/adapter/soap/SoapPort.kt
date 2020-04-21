package no.nav.dagpenger.journalføring.ferdigstill.adapter.soap

import javax.xml.namespace.QName
import no.nav.cxf.metrics.MetricFeature
import no.nav.tjeneste.virksomhet.behandlearbeidogaktivitetoppgave.v1.BehandleArbeidOgAktivitetOppgaveV1
import no.nav.tjeneste.virksomhet.ytelseskontrakt.v3.YtelseskontraktV3
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.ws.addressing.WSAddressingFeature

object SoapPort {

    fun ytelseskontraktV3(serviceUrl: String): YtelseskontraktV3 {
        return createServicePort(
            serviceUrl = serviceUrl,
            serviceClazz = YtelseskontraktV3::class.java,
            wsdl = "wsdl/tjenestespesifikasjon/no/nav/tjeneste/virksomhet/ytelseskontrakt/v3/Binding.wsdl",
            namespace = "http://nav.no/tjeneste/virksomhet/ytelseskontrakt/v3/Binding",
            svcName = "Ytelseskontrakt_v3",
            portName = "Ytelseskontrakt_v3Port"
        )
    }

    fun behandleArbeidOgAktivitetOppgaveV1(serviceUrl: String): BehandleArbeidOgAktivitetOppgaveV1 {

        return createServicePort(
            serviceUrl = serviceUrl,
            serviceClazz = BehandleArbeidOgAktivitetOppgaveV1::class.java,
            wsdl = "wsdl/no/nav/tjeneste/virksomhet/behandleArbeidOgAktivitetOppgave/v1/Binding.wsdl",
            namespace = "http://nav.no/tjeneste/virksomhet/behandleArbeidOgAktivitetOppgave/v1/Binding",
            svcName = "BehandleArbeidOgAktivitetOppgave_v1",
            portName = "BehandleArbeidOgAktivitetOppgave_v1Port"
        )
    }

    private fun <PORT_TYPE> createServicePort(
        serviceUrl: String,
        serviceClazz: Class<PORT_TYPE>,
        wsdl: String,
        namespace: String,
        svcName: String,
        portName: String
    ): PORT_TYPE {
        val factory = JaxWsProxyFactoryBean().apply {
            address = serviceUrl
            wsdlURL = wsdl
            serviceName = QName(namespace, svcName)
            endpointName = QName(namespace, portName)
            serviceClass = serviceClazz
            features = listOf(WSAddressingFeature(), MetricFeature())
            outInterceptors.add(CallIdInterceptor())
        }

        return factory.create(serviceClazz)
    }
}
