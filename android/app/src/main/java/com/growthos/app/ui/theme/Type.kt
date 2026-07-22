package com.growthos.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.Font
import com.growthos.app.R

// 产品定位:复盘手册。字体走"衬线标题 + 等宽数据"两类,不用系统默认 Roboto。
// 衬线(Noto Serif SC)给标题与金句,带手册的稳重感;等宽(JetBrains Mono)只给数字与时间,
// 强化"账本/记录"的符号感。正文用系统 Sans,长文可读。
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms.fonts",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val MonoFamily = FontFamily(
    Font(googleFont = GoogleFont("JetBrains Mono"), fontProvider = provider, weight = FontWeight.Medium)
)

val DisplayFamily = FontFamily(
    Font(googleFont = GoogleFont("Noto Serif SC"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Noto Serif SC"), fontProvider = provider, weight = FontWeight.Medium)
)

val GrowthOSTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 30.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
        letterSpacing = 0.2.sp
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        letterSpacing = 1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 13.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 15.sp
    )
)
