package no.nav.dagpenger.journalføring.ferdigstill.adapter

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.github.kittinunf.fuel.core.response
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

internal fun <T : Any> moshiDeserializerOf(clazz: Class<T>) = object : ResponseDeserializable<T> {
    override fun deserialize(content: String): T? = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(clazz)
        .fromJson(content)
}

internal inline fun <reified T : Any> Request.responseObject() = response(moshiDeserializerOf(T::class.java))
