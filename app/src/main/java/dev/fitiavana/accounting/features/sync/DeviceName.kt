package dev.fitiavana.accounting.features.sync

/** Friendly, deterministic device names such as "Strategic Banana", derived from the device id. */
object DeviceName {
    private val adjectives = listOf(
        "Agile", "Amber", "Bold", "Brave", "Bright", "Calm", "Clever", "Cosmic",
        "Crisp", "Curious", "Daring", "Eager", "Fancy", "Fearless", "Gentle", "Golden",
        "Happy", "Humble", "Jolly", "Keen", "Lively", "Lucky", "Mellow", "Mighty",
        "Nimble", "Noble", "Patient", "Playful", "Proud", "Quick", "Quiet", "Rapid",
        "Royal", "Rustic", "Sharp", "Shiny", "Silent", "Smooth", "Snappy", "Steady",
        "Strategic", "Sunny", "Swift", "Tidy", "Tough", "Vivid", "Warm", "Wild",
        "Wise", "Witty", "Zany", "Zesty", "Bouncy", "Chilly", "Dapper", "Epic",
        "Fuzzy", "Graceful", "Honest", "Jazzy", "Kind", "Loyal", "Merry", "Plucky",
    )
    private val nouns = listOf(
        "Apple", "Badger", "Banana", "Beaver", "Cactus", "Cherry", "Comet", "Coyote",
        "Dolphin", "Dragon", "Eagle", "Falcon", "Fox", "Giraffe", "Hedgehog", "Heron",
        "Iguana", "Jaguar", "Kiwi", "Koala", "Lemon", "Lion", "Lynx", "Mango",
        "Meteor", "Narwhal", "Otter", "Owl", "Panda", "Parrot", "Pear", "Penguin",
        "Pepper", "Phoenix", "Pine", "Quokka", "Rabbit", "Raven", "Robin", "Salmon",
        "Squirrel", "Tiger", "Tulip", "Turtle", "Unicorn", "Walnut", "Whale", "Willow",
        "Wolf", "Yak", "Zebra", "Acorn", "Bison", "Cobra", "Daisy", "Ember",
        "Ferret", "Gecko", "Hazel", "Ibis", "Maple", "Orchid", "Pebble", "Sparrow",
    )

    fun of(deviceId: String): String {
        val hash = deviceId.hashCode()
        val adjective = adjectives[Math.floorMod(hash, adjectives.size)]
        val noun = nouns[Math.floorMod(hash ushr 16, nouns.size)]
        return "$adjective $noun"
    }

    /** Short suffix to tell apart two peers that share the same generated name. */
    fun shortId(deviceId: String): String = deviceId.take(4).uppercase()
}
