package no.nav.dagpenger.journalføring.ferdigstill.adapter.soap

import de.huxhorn.sulky.ulid.ULID
import org.apache.cxf.binding.soap.SoapHeader
import org.apache.cxf.binding.soap.SoapMessage
import org.apache.cxf.interceptor.Fault
import org.apache.cxf.jaxb.JAXBDataBinding
import org.apache.cxf.message.Message
import org.apache.cxf.phase.AbstractPhaseInterceptor
import org.apache.cxf.phase.Phase
import org.slf4j.LoggerFactory
import javax.xml.bind.JAXBException
import javax.xml.namespace.QName

private val log = LoggerFactory.getLogger(CallIdInterceptor::class.java)
private val ulid = ULID()

class CallIdInterceptor : AbstractPhaseInterceptor<Message>(Phase.PRE_STREAM) {

    @Throws(Fault::class)
    override fun handleMessage(message: Message) {
        when (message) {
            is SoapMessage ->
                try {
                    val qName = QName("uri:no.nav.applikasjonsrammeverk", "callId")
                    val header = SoapHeader(qName, ulid.nextULID(), JAXBDataBinding(String::class.java))
                    message.headers.add(header)
                } catch (ex: JAXBException) {
                    log.warn("Error while setting CallId header", ex)
                }
        }
    }
}
