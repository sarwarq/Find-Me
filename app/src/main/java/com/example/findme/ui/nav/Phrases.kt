package com.example.findme.ui.nav

import com.example.findme.data.Geo
import com.example.findme.data.Maneuver
import com.example.findme.data.RoadEventKind
import com.example.findme.data.TransitStep
import com.example.findme.data.VoiceLanguage
import com.example.findme.data.rideName
import kotlin.math.roundToInt

/**
 * Everything guidance says, in one language. [VoicePrompter] decides what and when; this
 * decides the words. The Russian wording follows Organic Maps' `sound-strings/ru.json`.
 */
interface Phrases {
    /** "turn right", or null for going straight on (nothing to say). */
    fun action(m: Maneuver): String?
    /** "In 400 meters, turn right". */
    fun inDistance(meters: Double, action: String): String
    fun event(kind: RoadEventKind): String
    /** Said after a speed camera: ", limit 60". */
    fun limit(speed: Int): String
    fun takeRide(step: TransitStep): String
    /** Walk to the stop [stop], or to the destination when null. */
    fun walkTo(stop: String?): String
    fun stopReached(name: String): String
    val arrived: String
    val recalculating: String

    companion object {
        fun of(language: VoiceLanguage): Phrases = when (language) {
            VoiceLanguage.English -> EnglishPhrases
            VoiceLanguage.Russian -> RussianPhrases
        }
    }
}

object EnglishPhrases : Phrases {
    override fun action(m: Maneuver): String? = when (m) {
        Maneuver.Straight -> null
        Maneuver.SlightLeft -> "bear left"
        Maneuver.SlightRight -> "bear right"
        Maneuver.Left -> "turn left"
        Maneuver.Right -> "turn right"
        Maneuver.HardLeft -> "turn sharp left"
        Maneuver.HardRight -> "turn sharp right"
        Maneuver.ForkLeft -> "keep left"
        Maneuver.ForkRight -> "keep right"
        Maneuver.UturnLeft, Maneuver.UturnRight -> "make a U-turn"
        Maneuver.Roundabout -> "enter the roundabout"
        Maneuver.RoundaboutExit -> "take the exit"
        Maneuver.Finish -> "you will arrive at your destination"
    }

    override fun inDistance(meters: Double, action: String) = "In ${distanceWords(meters)}, $action"

    override fun event(kind: RoadEventKind): String = when (kind) {
        RoadEventKind.SpeedCamera -> "Speed camera ahead"
        RoadEventKind.LaneCamera -> "Lane camera ahead"
        RoadEventKind.Camera -> "Camera ahead"
        RoadEventKind.Police -> "Police ahead"
        RoadEventKind.Accident -> "Accident ahead"
        RoadEventKind.Works -> "Road works ahead"
        RoadEventKind.Closed -> "Road closed ahead"
        RoadEventKind.Danger -> "Caution ahead"
        RoadEventKind.School -> "School zone ahead"
    }

    override fun limit(speed: Int) = ", limit $speed"

    override fun takeRide(step: TransitStep): String {
        val ride = rideName(step).replaceFirst("Ride", "line").replaceFirstChar { it.lowercase() }
        return "Take $ride" + step.toStop?.let { " to $it" }.orEmpty()
    }

    override fun walkTo(stop: String?) = if (stop != null) "Walk to the stop $stop" else "Walk to your destination"
    override fun stopReached(name: String) = "You have reached $name"
    override val arrived = "You have arrived"
    override val recalculating = "Recalculating the route"

    /** "300 meters", "1.5 kilometers": rounded the way people say it. */
    fun distanceWords(meters: Double): String {
        if (Geo.imperial) return imperialWords(meters)
        val m = roundMeters(meters)
        if (m < 1000) return "$m meters"
        val km = halves(meters / 1000)
        return when {
            km == 1.0 -> "1 kilometer"
            km % 1.0 == 0.0 -> "${km.toInt()} kilometers"
            else -> "$km kilometers"
        }
    }

    /** "500 feet", "0.3 miles", "1.5 miles". */
    private fun imperialWords(meters: Double): String {
        val miles = meters / Geo.METERS_PER_MILE
        if (miles < 0.1) return "${roundFeet(meters)} feet"
        if (miles < 1) return "${(miles * 10).roundToInt() / 10.0} miles"
        val half = halves(miles)
        return when {
            half == 1.0 -> "1 mile"
            half % 1.0 == 0.0 -> "${half.toInt()} miles"
            else -> "$half miles"
        }
    }
}

/**
 * Russian, numbers in words like Organic Maps ("Через полтора километра"): a speech engine
 * reads "1,5" as "одна целая пять десятых", and gets the case of a number wrong.
 */
object RussianPhrases : Phrases {
    override fun action(m: Maneuver): String? = when (m) {
        Maneuver.Straight -> null
        Maneuver.SlightLeft -> "держитесь левее"
        Maneuver.SlightRight -> "держитесь правее"
        Maneuver.Left -> "поверните налево"
        Maneuver.Right -> "поверните направо"
        Maneuver.HardLeft -> "резко поверните налево"
        Maneuver.HardRight -> "резко поверните направо"
        Maneuver.ForkLeft -> "на развилке держитесь левее"
        Maneuver.ForkRight -> "на развилке держитесь правее"
        Maneuver.UturnLeft, Maneuver.UturnRight -> "развернитесь"
        Maneuver.Roundabout -> "поверните на кольцо"
        Maneuver.RoundaboutExit -> "сверните с кольца"
        Maneuver.Finish -> "вы прибудете в пункт назначения"
    }

    override fun inDistance(meters: Double, action: String) = "Через ${distanceWords(meters)} $action"

    override fun event(kind: RoadEventKind): String = when (kind) {
        RoadEventKind.SpeedCamera -> "Впереди камера контроля скорости"
        RoadEventKind.LaneCamera -> "Впереди камера контроля полосы"
        RoadEventKind.Camera -> "Впереди камера"
        RoadEventKind.Police -> "Впереди пост полиции"
        RoadEventKind.Accident -> "Впереди авария"
        RoadEventKind.Works -> "Впереди дорожные работы"
        RoadEventKind.Closed -> "Впереди дорога перекрыта"
        RoadEventKind.Danger -> "Впереди опасный участок"
        RoadEventKind.School -> "Впереди школа"
    }

    override fun limit(speed: Int) = ", ограничение ${number(speed)}"

    override fun takeRide(step: TransitStep): String {
        // "на" takes the accusative: на автобус, на маршрутку.
        val kind = when (step.vehicle) {
            "bus" -> "автобус"
            "minibus" -> "маршрутку"
            "trolleybus" -> "троллейбус"
            "tramway", "tram" -> "трамвай"
            "underground" -> "метро"
            "railway", "suburban" -> "поезд"
            else -> "маршрут"
        }
        val ride = listOfNotNull(kind, step.lineName).joinToString(" ")
        return "Садитесь на $ride" + step.toStop?.let { " до остановки $it" }.orEmpty()
    }

    override fun walkTo(stop: String?) = if (stop != null) "Идите к остановке $stop" else "Идите к пункту назначения"
    override fun stopReached(name: String) = "Вы достигли точки: $name"
    override val arrived = "Вы прибыли в пункт назначения"
    override val recalculating = "Пересчёт маршрута"

    /** "через …": "четыреста метров", "километр", "полтора километра", "две мили". */
    fun distanceWords(meters: Double): String {
        if (Geo.imperial) {
            val miles = meters / Geo.METERS_PER_MILE
            if (miles < 1) {
                val ft = roundFeet(meters)
                return "${number(ft)} ${plural(ft, "фут", "фута", "футов")}"
            }
            return halvesWords(halves(miles), feminine = true, "милю", "мили", "миль", "полторы")
        }
        val m = roundMeters(meters)
        if (m < 1000) return "${number(m)} ${plural(m, "метр", "метра", "метров")}"
        return halvesWords(halves(meters / 1000), feminine = false, "километр", "километра", "километров", "полтора")
    }

    /** 1 → "километр" / "одну милю", 1.5 → "полтора километра", 2.5 → "два с половиной километра". */
    private fun halvesWords(
        x: Double, feminine: Boolean, one: String, few: String, many: String, oneAndHalf: String,
    ): String {
        val whole = x.toInt()
        if (x % 1.0 != 0.0) return if (whole == 1) "$oneAndHalf $few" else "${number(whole, feminine)} с половиной $few"
        if (whole == 1) return if (feminine) "одну $one" else one
        return "${number(whole, feminine)} ${plural(whole, one, few, many)}"
    }

    /** 1 метр, 2 метра, 5 метров, 21 метр, 12 метров. */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val d = n % 10
        val dd = n % 100
        return when {
            d == 1 && dd != 11 -> one
            d in 2..4 && dd !in 12..14 -> few
            else -> many
        }
    }

    private val ones = listOf("", "один", "два", "три", "четыре", "пять", "шесть", "семь", "восемь", "девять")
    private val teens = listOf(
        "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
        "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать",
    )
    private val tens = listOf(
        "", "", "двадцать", "тридцать", "сорок", "пятьдесят", "шестьдесят", "семьдесят", "восемьдесят", "девяносто",
    )
    private val hundreds = listOf(
        "", "сто", "двести", "триста", "четыреста", "пятьсот", "шестьсот", "семьсот", "восемьсот", "девятьсот",
    )

    /**
     * [n] (0..999 999) in words, in the accusative after "через": "тысячу пятьсот",
     * "двадцать одну милю" with [feminine].
     */
    fun number(n: Int, feminine: Boolean = false): String {
        if (n == 0) return "ноль"
        val parts = ArrayList<String>()
        val th = n / 1000
        if (th > 0) {
            if (th == 1) parts += "тысячу"
            else {
                parts += below1000(th, feminine = true)
                parts += plural(th, "тысячу", "тысячи", "тысяч")
            }
        }
        if (n % 1000 > 0) parts += below1000(n % 1000, feminine)
        return parts.joinToString(" ")
    }

    private fun below1000(n: Int, feminine: Boolean): String {
        val words = ArrayList<String>()
        if (n >= 100) words += hundreds[n / 100]
        val r = n % 100
        if (r in 10..19) {
            words += teens[r - 10]
        } else {
            if (r >= 20) words += tens[r / 10]
            val d = r % 10
            if (d > 0) words += when {
                feminine && d == 1 -> "одну"
                feminine && d == 2 -> "две"
                else -> ones[d]
            }
        }
        return words.joinToString(" ")
    }
}

/** Meters rounded the way people say them: by 50 below 200 m, by 100 above; 50 at least. */
private fun roundMeters(meters: Double): Int {
    val step = if (meters < 200) 50 else 100
    return ((meters / step).roundToInt() * step).coerceAtLeast(50)
}

/** Feet by the hundred, 100 at least. */
private fun roundFeet(meters: Double): Int =
    ((meters * Geo.FEET_PER_METER / 100).roundToInt() * 100).coerceAtLeast(100)

/** To the nearest half: 1.0, 1.5, 2.0... */
private fun halves(x: Double): Double = (x * 2).roundToInt() / 2.0
