package com.example.ui.util

/**
 * Every country the shop could be calling from, with its dial code.
 *
 * The owner picks the country rather than typing a code, because nobody in a
 * hurry should have to remember that Sri Lanka is 94 and not 944. The dial code
 * is then shown in front of the number box, so what is typed and what is stored
 * can never drift apart.
 */
data class Country(
    val name: String,
    val dialCode: String,
    /** ISO 3166-1 alpha-2, used only for sorting and searching. */
    val code: String,
    /**
     * Exact length of the local number when a country fixes one. Sri Lanka is
     * nine digits once the leading 0 is dropped; most countries vary.
     */
    val exactLocalLength: Int? = null
)

object CountryCodes {

    val all: List<Country> = listOf(
        Country("Afghanistan", "+93", "AF"),
        Country("Albania", "+355", "AL"),
        Country("Algeria", "+213", "DZ"),
        Country("American Samoa", "+1684", "AS"),
        Country("Andorra", "+376", "AD"),
        Country("Angola", "+244", "AO"),
        Country("Anguilla", "+1264", "AI"),
        Country("Antarctica", "+672", "AQ"),
        Country("Antigua and Barbuda", "+1268", "AG"),
        Country("Argentina", "+54", "AR"),
        Country("Armenia", "+374", "AM"),
        Country("Aruba", "+297", "AW"),
        Country("Australia", "+61", "AU"),
        Country("Austria", "+43", "AT"),
        Country("Azerbaijan", "+994", "AZ"),
        Country("Bahamas", "+1242", "BS"),
        Country("Bahrain", "+973", "BH"),
        Country("Bangladesh", "+880", "BD"),
        Country("Barbados", "+1246", "BB"),
        Country("Belarus", "+375", "BY"),
        Country("Belgium", "+32", "BE"),
        Country("Belize", "+501", "BZ"),
        Country("Benin", "+229", "BJ"),
        Country("Bermuda", "+1441", "BM"),
        Country("Bhutan", "+975", "BT"),
        Country("Bolivia", "+591", "BO"),
        Country("Bosnia and Herzegovina", "+387", "BA"),
        Country("Botswana", "+267", "BW"),
        Country("Brazil", "+55", "BR"),
        Country("British Indian Ocean Territory", "+246", "IO"),
        Country("Brunei", "+673", "BN"),
        Country("Bulgaria", "+359", "BG"),
        Country("Burkina Faso", "+226", "BF"),
        Country("Burundi", "+257", "BI"),
        Country("Cabo Verde", "+238", "CV"),
        Country("Cambodia", "+855", "KH"),
        Country("Cameroon", "+237", "CM"),
        Country("Canada", "+1", "CA"),
        Country("Cayman Islands", "+1345", "KY"),
        Country("Central African Republic", "+236", "CF"),
        Country("Chad", "+235", "TD"),
        Country("Chile", "+56", "CL"),
        Country("China", "+86", "CN"),
        Country("Christmas Island", "+61", "CX"),
        Country("Cocos (Keeling) Islands", "+61", "CC"),
        Country("Colombia", "+57", "CO"),
        Country("Comoros", "+269", "KM"),
        Country("Congo", "+242", "CG"),
        Country("Congo, Democratic Republic of the", "+243", "CD"),
        Country("Cook Islands", "+682", "CK"),
        Country("Costa Rica", "+506", "CR"),
        Country("Croatia", "+385", "HR"),
        Country("Cuba", "+53", "CU"),
        Country("Curacao", "+599", "CW"),
        Country("Cyprus", "+357", "CY"),
        Country("Czechia", "+420", "CZ"),
        Country("Denmark", "+45", "DK"),
        Country("Djibouti", "+253", "DJ"),
        Country("Dominica", "+1767", "DM"),
        Country("Dominican Republic", "+1809", "DO"),
        Country("Ecuador", "+593", "EC"),
        Country("Egypt", "+20", "EG"),
        Country("El Salvador", "+503", "SV"),
        Country("Equatorial Guinea", "+240", "GQ"),
        Country("Eritrea", "+291", "ER"),
        Country("Estonia", "+372", "EE"),
        Country("Eswatini", "+268", "SZ"),
        Country("Ethiopia", "+251", "ET"),
        Country("Falkland Islands", "+500", "FK"),
        Country("Faroe Islands", "+298", "FO"),
        Country("Fiji", "+679", "FJ"),
        Country("Finland", "+358", "FI"),
        Country("France", "+33", "FR"),
        Country("French Guiana", "+594", "GF"),
        Country("French Polynesia", "+689", "PF"),
        Country("Gabon", "+241", "GA"),
        Country("Gambia", "+220", "GM"),
        Country("Georgia", "+995", "GE"),
        Country("Germany", "+49", "DE"),
        Country("Ghana", "+233", "GH"),
        Country("Gibraltar", "+350", "GI"),
        Country("Greece", "+30", "GR"),
        Country("Greenland", "+299", "GL"),
        Country("Grenada", "+1473", "GD"),
        Country("Guadeloupe", "+590", "GP"),
        Country("Guam", "+1671", "GU"),
        Country("Guatemala", "+502", "GT"),
        Country("Guernsey", "+44", "GG"),
        Country("Guinea", "+224", "GN"),
        Country("Guinea-Bissau", "+245", "GW"),
        Country("Guyana", "+592", "GY"),
        Country("Haiti", "+509", "HT"),
        Country("Honduras", "+504", "HN"),
        Country("Hong Kong", "+852", "HK"),
        Country("Hungary", "+36", "HU"),
        Country("Iceland", "+354", "IS"),
        Country("India", "+91", "IN"),
        Country("Indonesia", "+62", "ID"),
        Country("Iran", "+98", "IR"),
        Country("Iraq", "+964", "IQ"),
        Country("Ireland", "+353", "IE"),
        Country("Isle of Man", "+44", "IM"),
        Country("Israel", "+972", "IL"),
        Country("Italy", "+39", "IT"),
        Country("Ivory Coast", "+225", "CI"),
        Country("Jamaica", "+1876", "JM"),
        Country("Japan", "+81", "JP"),
        Country("Jersey", "+44", "JE"),
        Country("Jordan", "+962", "JO"),
        Country("Kazakhstan", "+7", "KZ"),
        Country("Kenya", "+254", "KE"),
        Country("Kiribati", "+686", "KI"),
        Country("Kosovo", "+383", "XK"),
        Country("Kuwait", "+965", "KW"),
        Country("Kyrgyzstan", "+996", "KG"),
        Country("Laos", "+856", "LA"),
        Country("Latvia", "+371", "LV"),
        Country("Lebanon", "+961", "LB"),
        Country("Lesotho", "+266", "LS"),
        Country("Liberia", "+231", "LR"),
        Country("Libya", "+218", "LY"),
        Country("Liechtenstein", "+423", "LI"),
        Country("Lithuania", "+370", "LT"),
        Country("Luxembourg", "+352", "LU"),
        Country("Macao", "+853", "MO"),
        Country("Madagascar", "+261", "MG"),
        Country("Malawi", "+265", "MW"),
        Country("Malaysia", "+60", "MY"),
        Country("Maldives", "+960", "MV"),
        Country("Mali", "+223", "ML"),
        Country("Malta", "+356", "MT"),
        Country("Marshall Islands", "+692", "MH"),
        Country("Martinique", "+596", "MQ"),
        Country("Mauritania", "+222", "MR"),
        Country("Mauritius", "+230", "MU"),
        Country("Mayotte", "+262", "YT"),
        Country("Mexico", "+52", "MX"),
        Country("Micronesia", "+691", "FM"),
        Country("Moldova", "+373", "MD"),
        Country("Monaco", "+377", "MC"),
        Country("Mongolia", "+976", "MN"),
        Country("Montenegro", "+382", "ME"),
        Country("Montserrat", "+1664", "MS"),
        Country("Morocco", "+212", "MA"),
        Country("Mozambique", "+258", "MZ"),
        Country("Myanmar", "+95", "MM"),
        Country("Namibia", "+264", "NA"),
        Country("Nauru", "+674", "NR"),
        Country("Nepal", "+977", "NP"),
        Country("Netherlands", "+31", "NL"),
        Country("New Caledonia", "+687", "NC"),
        Country("New Zealand", "+64", "NZ"),
        Country("Nicaragua", "+505", "NI"),
        Country("Niger", "+227", "NE"),
        Country("Nigeria", "+234", "NG"),
        Country("Niue", "+683", "NU"),
        Country("North Korea", "+850", "KP"),
        Country("North Macedonia", "+389", "MK"),
        Country("Norway", "+47", "NO"),
        Country("Oman", "+968", "OM"),
        Country("Pakistan", "+92", "PK"),
        Country("Palau", "+680", "PW"),
        Country("Palestine", "+970", "PS"),
        Country("Panama", "+507", "PA"),
        Country("Papua New Guinea", "+675", "PG"),
        Country("Paraguay", "+595", "PY"),
        Country("Peru", "+51", "PE"),
        Country("Philippines", "+63", "PH"),
        Country("Pitcairn Islands", "+64", "PN"),
        Country("Poland", "+48", "PL"),
        Country("Portugal", "+351", "PT"),
        Country("Puerto Rico", "+1787", "PR"),
        Country("Qatar", "+974", "QA"),
        Country("Reunion", "+262", "RE"),
        Country("Romania", "+40", "RO"),
        Country("Russia", "+7", "RU"),
        Country("Rwanda", "+250", "RW"),
        Country("Samoa", "+685", "WS"),
        Country("San Marino", "+378", "SM"),
        Country("Sao Tome and Principe", "+239", "ST"),
        Country("Saudi Arabia", "+966", "SA"),
        Country("Senegal", "+221", "SN"),
        Country("Serbia", "+381", "RS"),
        Country("Seychelles", "+248", "SC"),
        Country("Sierra Leone", "+232", "SL"),
        Country("Singapore", "+65", "SG"),
        Country("Sint Maarten", "+1721", "SX"),
        Country("Slovakia", "+421", "SK"),
        Country("Slovenia", "+386", "SI"),
        Country("Solomon Islands", "+677", "SB"),
        Country("Somalia", "+252", "SO"),
        Country("South Africa", "+27", "ZA"),
        Country("South Korea", "+82", "KR"),
        Country("South Sudan", "+211", "SS"),
        Country("Spain", "+34", "ES"),
        Country("Sri Lanka", "+94", "LK", exactLocalLength = 9),
        Country("Sudan", "+249", "SD"),
        Country("Suriname", "+597", "SR"),
        Country("Svalbard and Jan Mayen", "+47", "SJ"),
        Country("Sweden", "+46", "SE"),
        Country("Switzerland", "+41", "CH"),
        Country("Syria", "+963", "SY"),
        Country("Taiwan", "+886", "TW"),
        Country("Tajikistan", "+992", "TJ"),
        Country("Tanzania", "+255", "TZ"),
        Country("Thailand", "+66", "TH"),
        Country("Timor-Leste", "+670", "TL"),
        Country("Togo", "+228", "TG"),
        Country("Tokelau", "+690", "TK"),
        Country("Tonga", "+676", "TO"),
        Country("Trinidad and Tobago", "+1868", "TT"),
        Country("Tunisia", "+216", "TN"),
        Country("Turkiye", "+90", "TR"),
        Country("Turkmenistan", "+993", "TM"),
        Country("Turks and Caicos Islands", "+1649", "TC"),
        Country("Tuvalu", "+688", "TV"),
        Country("Uganda", "+256", "UG"),
        Country("Ukraine", "+380", "UA"),
        Country("United Arab Emirates", "+971", "AE"),
        Country("United Kingdom", "+44", "GB"),
        Country("United States", "+1", "US"),
        Country("Uruguay", "+598", "UY"),
        Country("Uzbekistan", "+998", "UZ"),
        Country("Vanuatu", "+678", "VU"),
        Country("Vatican City", "+39", "VA"),
        Country("Venezuela", "+58", "VE"),
        Country("Vietnam", "+84", "VN"),
        Country("Wallis and Futuna", "+681", "WF"),
        Country("Western Sahara", "+212", "EH"),
        Country("Yemen", "+967", "YE"),
        Country("Zambia", "+260", "ZM"),
        Country("Zimbabwe", "+263", "ZW")
    )

    /** The country a new shop starts on. */
    val default: Country = all.first { it.code == "LK" }

    fun findByDialCode(dialCode: String): Country? {
        val wanted = dialCode.trim().removePrefix("+")
        if (wanted.isBlank()) return null
        return all.firstOrNull { it.dialCode.removePrefix("+") == wanted }
    }

    fun findByCode(code: String): Country? =
        all.firstOrNull { it.code.equals(code.trim(), ignoreCase = true) }

    /**
     * Splits a stored number back into its dial code and local part.
     *
     * Shop numbers saved before the country picker were typed whole
     * ("0771234567"), so anything we cannot match falls back to the default
     * country with the leading 0 dropped.
     */
    fun split(stored: String): Pair<Country, String> {
        val raw = stored.trim()
        if (raw.startsWith("+")) {
            val digits = raw.substring(1).filter { it.isDigit() }
            // Longest dial code first, so +1 never wins over +1242.
            val match = all
                .sortedByDescending { it.dialCode.removePrefix("+").length }
                .firstOrNull { digits.startsWith(it.dialCode.removePrefix("+")) }
            if (match != null) {
                return match to digits.substring(match.dialCode.removePrefix("+").length)
            }
        }
        val local = raw.filter { it.isDigit() }.trimStart('0')
        return default to local
    }

    /** The number as it should be stored and printed: "+94 777777700". */
    fun join(country: Country, local: String): String =
        "${country.dialCode} ${local.trim()}".trim()
}

/**
 * Phone number rules, in one place so the setup wizard and Settings agree.
 *
 * The two mistakes a shop makes are typing the leading zero the network uses
 * at home ("0777777700") and stopping halfway ("7777777"), so those are the two
 * things this checks by name.
 */
object PhoneValidator {

    fun digitsOnly(input: String): String = input.filter { it.isDigit() }

    /** Drops a leading 0 rather than rejecting the whole number. */
    fun localPart(input: String): String = digitsOnly(input).trimStart('0')

    fun errorFor(country: Country, local: String): String? {
        val digits = digitsOnly(local)
        return when {
            digits.isEmpty() -> "Enter your phone number"
            local.trimStart().startsWith("0") ->
                "Leave out the first 0 — ${country.dialCode} already covers it"

            country.exactLocalLength != null && digits.length != country.exactLocalLength ->
                "A ${country.name} number has exactly ${country.exactLocalLength} digits"

            digits.length < 4 -> "That is too short for a phone number"
            digits.length > 13 -> "That is too long for a phone number"
            else -> null
        }
    }
}
