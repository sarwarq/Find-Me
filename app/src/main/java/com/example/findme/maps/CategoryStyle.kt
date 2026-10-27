package com.example.findme.maps

import androidx.annotation.DrawableRes
import com.example.findme.R

/** Pin icon and color for a MapKit category class, close to Yandex rubric styles. */
data class CategoryStyle(@param:DrawableRes val icon: Int, val color: Int)

object CategoryStyles {
    private const val RED = 0xFFFA453A.toInt()
    private const val ORANGE = 0xFFFF8A00.toInt()
    private const val GREEN = 0xFF2FA84F.toInt()
    private const val PURPLE = 0xFF8A4FFF.toInt()
    private const val BLUE = 0xFF196DFF.toInt()
    private const val SLATE = 0xFF5C6B80.toInt()
    private const val PINK = 0xFFE64A8A.toInt()
    private const val MAGENTA = 0xFFB0559E.toInt()
    private const val GRAY = 0xFF6E7FAA.toInt()

    private val rules: List<Pair<List<String>, CategoryStyle>> = listOf(
        listOf("restaurant", "food", "fast", "ресторан", "еда", "столов", "фастфуд", "пицц", "суши") to CategoryStyle(R.drawable.ms_restaurant, ORANGE),
        listOf("cafe", "coffee", "кафе", "кофе", "кондитер", "пекарн") to CategoryStyle(R.drawable.ms_local_cafe, ORANGE),
        listOf("beauty", "hairdress", "barber", "spa", "nail", "салон красоты", "парикмахер", "барбер") to CategoryStyle(R.drawable.ms_content_cut, PINK),
        listOf("bar", "pub", "бар", "паб") to CategoryStyle(R.drawable.ms_wine_bar, ORANGE),
        listOf("pharmac", "drugstore", "аптек") to CategoryStyle(R.drawable.ms_local_pharmacy, GREEN),
        listOf("hospital", "medicine", "clinic", "polyclinic", "dentist", "больниц", "клиник", "поликлиник", "медиц", "стоматолог") to
            CategoryStyle(R.drawable.ms_medical_services, GREEN),
        listOf("school", "university", "college", "kindergarten", "education", "школ", "университет", "колледж", "детский сад", "лицей", "гимназ", "образов") to
            CategoryStyle(R.drawable.ms_school, RED),
        listOf("supermarket", "grocer", "products", "супермаркет", "продукт", "магазин продуктов") to CategoryStyle(R.drawable.ms_shopping_basket, PURPLE),
        listOf("mall", "shop", "store", "market", "торговый", "магазин", "рынок") to CategoryStyle(R.drawable.ms_local_mall, PURPLE),
        listOf("atm", "банкомат") to CategoryStyle(R.drawable.ms_local_atm, BLUE),
        listOf("bank", "банк") to CategoryStyle(R.drawable.ms_account_balance, BLUE),
        listOf("parking", "парковк", "стоянк") to CategoryStyle(R.drawable.ms_local_parking, BLUE),
        listOf("gas", "fuel", "petrol", "азс", "заправ") to CategoryStyle(R.drawable.ms_local_gas_station, SLATE),
        listOf("car wash", "carwash", "автомойк") to CategoryStyle(R.drawable.ms_local_car_wash, SLATE),
        listOf("auto", "car", "tire", "tyre", "автосервис", "шиномонтаж", "авто") to CategoryStyle(R.drawable.ms_car_repair, SLATE),
        listOf("hotel", "hostel", "гостиниц", "отель", "хостел") to CategoryStyle(R.drawable.ms_hotel, PINK),
        listOf("cinema", "theatre", "theater", "кинотеатр", "театр") to CategoryStyle(R.drawable.ms_videocam, MAGENTA),
        listOf("museum", "gallery", "landmark", "monument", "музей", "галере", "памятник", "достопримеч") to CategoryStyle(R.drawable.ms_museum, MAGENTA),
        listOf("park", "garden", "парк", "сквер", "сад") to CategoryStyle(R.drawable.ms_park, GREEN),
        listOf("fitness", "gym", "sport", "фитнес", "спорт", "тренажер") to CategoryStyle(R.drawable.ms_fitness_center, SLATE),
        listOf("mosque", "church", "temple", "religio", "мечеть", "церков", "храм") to CategoryStyle(R.drawable.ms_church, SLATE),
        listOf("post", "почт") to CategoryStyle(R.drawable.ms_local_post_office, BLUE),
        listOf("flower", "цвет") to CategoryStyle(R.drawable.ms_local_florist, PINK),
        listOf("pet", "veterinar", "ветеринар", "зоомагазин") to CategoryStyle(R.drawable.ms_pets, ORANGE),
        listOf("metro", "subway", "метро") to CategoryStyle(R.drawable.ms_subway, RED),
        listOf("bus", "stop", "transit", "остановк", "автобус") to CategoryStyle(R.drawable.ms_directions_bus, BLUE),
    )

    private val default = CategoryStyle(R.drawable.ms_location_on, GRAY)
    private val address = CategoryStyle(R.drawable.ms_apartment, GRAY)

    fun of(categoryClass: String?, categoryName: String? = null, isBusiness: Boolean = true): CategoryStyle {
        if (!isBusiness) return address
        val key = listOfNotNull(categoryClass, categoryName).joinToString(" ").lowercase()
        if (key.isBlank()) return default
        return rules.firstOrNull { (words, _) -> words.any { it in key } }?.second ?: default
    }
}
