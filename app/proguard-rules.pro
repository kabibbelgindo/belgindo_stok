# Aturan ProGuard/R8. Minify dinonaktifkan secara default; aturan ini tetap
# disiapkan bila isMinifyEnabled diaktifkan kelak.
-keep class com.belgindo.stok.** { *; }
-dontwarn okhttp3.**
