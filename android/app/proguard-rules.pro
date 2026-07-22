# GrowthOS ProGuard / R8 规则(发布构建)
#
# minifyEnabled true + shrinkResources true 时,R8 会裁剪未用代码、混淆名称。
# 以下保留规则覆盖框架反射要求,避免运行时崩溃。

# ---------- kotlinx-serialization ----------
# @Serializable 类与生成的 serializer 通过反射访问,必须保留。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 项目里所有 @Serializable 数据类(五表实体 + ExportPayload 等)
-keep @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Room ----------
# 实体类字段通过反射访问,DAO 实现由 KSP 生成。
-keep class com.growthos.app.data.local.entity.** { *; }
-keep class com.growthos.app.data.local.relation.** { *; }
-keep class com.growthos.app.data.local.dao.** { *; }
-keep class com.growthos.app.data.local.GrowthOSDatabase { *; }
-keep class com.growthos.app.data.local.Converters { *; }
-keep class com.growthos.app.data.local.ErrorTypeSeed { *; }

# 枚举(归因 / 训练状态):valueOf 通过反射,需保留枚举常量。
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---------- Compose / AndroidX ----------
# Compose 运行时规则已由 androidx.compose.compiler 内置 consumer-rules 提供,
# 此处不重复。Material3 / Activity / Lifecycle 同理。

# ---------- 应用入口 ----------
-keep class com.growthos.app.GrowthOSApp { *; }
-keep class com.growthos.app.MainActivity { *; }
