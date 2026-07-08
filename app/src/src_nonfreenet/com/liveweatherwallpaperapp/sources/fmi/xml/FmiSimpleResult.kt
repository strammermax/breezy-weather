package com.liveweatherwallpaperapp.sources.fmi.xml

import com.liveweatherwallpaperapp.common.serializer.DateSerializer
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import java.util.Date

@Serializable
@XmlSerialName("FeatureCollection", NS_WFS, "fmi")
data class FmiSimpleResult(
    val members: List<Member>? = null,
) {
    @Serializable
    @XmlSerialName("member", NS_WFS, "fmi")
    data class Member(
        val bsWfsElement: BsWfsElement?,
    ) {
        @Serializable
        @XmlSerialName("BsWfsElement", NS_BSWFS, "fmi")
        data class BsWfsElement(
            val time: Time?,
            val parameterName: ParameterName?,
            val parameterValue: ParameterValue?,
        ) {
            @Serializable
            @XmlSerialName("Time", NS_BSWFS, "fmi")
            data class Time(
                @XmlValue(true) @Serializable(DateSerializer::class) val value: Date?,
            )

            @Serializable
            @XmlSerialName("ParameterName", NS_BSWFS, "fmi")
            data class ParameterName(
                @XmlValue(true) val value: String?,
            )

            @Serializable
            @XmlSerialName("ParameterValue", NS_BSWFS, "fmi")
            data class ParameterValue(
                @XmlValue(true) val value: String?,
            )
        }
    }
}

private const val NS_WFS = "http://www.opengis.net/wfs/2.0"
private const val NS_BSWFS = "http://xml.fmi.fi/schema/wfs/2.0"
