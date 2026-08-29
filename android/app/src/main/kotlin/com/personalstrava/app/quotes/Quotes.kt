package com.personalstrava.app.quotes

import java.time.LocalDate

/**
 * Purely for fun — shown on Home above the record buttons (spec follow-up ask: "some fun
 * motivational quotes"). Deterministic per calendar day (day-of-year mod list size) rather than
 * random per screen visit, so it doesn't flicker to a different line every time Home recomposes
 * or you back out of a recording — same quote all day, a new one tomorrow.
 */
object Quotes {
    private val quotes = listOf(
        "The only bad workout is the one that stayed a good intention.",
        "Sweat now, brag later.",
        "Your couch will still be there when you get back.",
        "Slow is a pace. Stopped is not.",
        "Nobody regrets a walk once they're five minutes into it.",
        "Future you is already thanking present you.",
        "Motivation gets you started. This app just wants you to press the button.",
        "Great rides start with mediocre motivation.",
        "You don't have to be fast. You have to be out the door.",
        "Today's forecast: 100% chance of feeling better after.",
        "Consistency beats intensity — but both together is nice too.",
        "The hardest part is putting your shoes on. You've got this.",
        "Somewhere, a past version of you is proud right now.",
        "A bad ride still beats a good excuse.",
        "Step one: start. Step two: everything else is easier.",
        "You've never once regretted going. Statistically speaking.",
        "Do it for the shareable card at the end.",
        "Small steps still count as steps.",
        "The road doesn't care how you feel about it yet.",
        "Warm up. Show up. That's most of it.",
        "Legs feel heavy? Perfect, that's the sign you needed this.",
        "Ten minutes in, you'll wonder why you almost skipped this.",
        "Your streak is watching.",
        "Nobody ever finished a workout and felt worse about their day.",
        "Go be insufferably healthy for the next hour.",
        "The best time was yesterday. The next best time is now.",
        "You're one tap away from being the kind of person who did the thing.",
        "Progress doesn't check the weather forecast.",
        "Move today, thank yourself tomorrow.",
        "Discipline is just motivation that showed up anyway.",
    )

    fun quoteOfTheDay(): String = quotes[LocalDate.now().dayOfYear % quotes.size]
}
