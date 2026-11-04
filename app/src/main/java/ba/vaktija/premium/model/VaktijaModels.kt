package ba.vaktija.premium.model

data class VaktijaLocation(
    val id: Int,
    val name: String
)

data class VaktijaDay(
    val locationId: Int,
    val locationName: String,
    val hijriDate: String,
    val gregorianDate: String,
    val vakatTimes: List<String>
) {
    companion object {
        val VAKAT_NAMES = listOf(
            "Zora",
            "Izlazak sunca",
            "Podne",
            "Ikindija",
            "Akšam",
            "Jacija"
        )
        val SHORT_NAMES = listOf(
            "Zora",
            "Izlazak",
            "Podne",
            "Ikindija",
            "Akšam",
            "Jacija"
        )
    }
}

object VaktijaLocations {
    val ALL_NAMES = listOf(
        "Banovići", "Banja Luka", "Bihać", "Bijeljina", "Bileća", "Bosanski Brod",
        "Bosanska Dubica", "Bosanska Gradiška", "Bosansko Grahovo", "Bosanska Krupa",
        "Bosanski Novi", "Bosanski Petrovac", "Bosanski Šamac", "Bratunac", "Brčko",
        "Breza", "Bugojno", "Busovača", "Bužim", "Cazin", "Čajniče", "Čapljina",
        "Čelić", "Čelinac", "Čitluk", "Derventa", "Doboj", "Donji Vakuf", "Drvar",
        "Foča", "Fojnica", "Gacko", "Glamoč", "Goražde", "Gornji Vakuf", "Gračanica",
        "Gradačac", "Grude", "Hadžići", "Han-Pijesak", "Hlivno", "Ilijaš", "Jablanica",
        "Jajce", "Kakanj", "Kalesija", "Kalinovik", "Kiseljak", "Kladanj", "Ključ",
        "Konjic", "Kotor-Varoš", "Kreševo", "Kupres", "Laktaši", "Lopare", "Lukavac",
        "Ljubinje", "Ljubuški", "Maglaj", "Modriča", "Mostar", "Mrkonjić-Grad", "Neum",
        "Nevesinje", "Novi Travnik", "Odžak", "Olovo", "Orašje", "Pale", "Posušje",
        "Prijedor", "Prnjavor", "Prozor", "Rogatica", "Rudo", "Sanski Most", "Sarajevo",
        "Skender-Vakuf", "Sokolac", "Srbac", "Srebrenica", "Srebrenik", "Stolac",
        "Šekovići", "Šipovo", "Široki Brijeg", "Teslić", "Tešanj", "Tomislav-Grad",
        "Travnik", "Trebinje", "Trnovo", "Tuzla", "Ugljevik", "Vareš", "Velika Kladuša",
        "Visoko", "Višegrad", "Vitez", "Vlasenica", "Zavidovići", "Zenica", "Zvornik",
        "Žepa", "Žepče", "Živinice", "Bijelo Polje", "Gusinje", "Nova Varoš",
        "Novi Pazar", "Plav", "Pljevlja", "Priboj", "Prijepolje", "Rožaje", "Sjenica", "Tutin"
    )

    val ALL: List<VaktijaLocation> = ALL_NAMES.mapIndexed { idx, name ->
        VaktijaLocation(id = idx, name = name)
    }

    const val DEFAULT_LOCATION_ID = 93 // Tuzla

    fun byId(id: Int): VaktijaLocation =
        ALL.getOrElse(id) { VaktijaLocation(DEFAULT_LOCATION_ID, "Tuzla") }
}
