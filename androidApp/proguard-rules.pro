# kotlinx.serialization keeps its generated serializers via its own consumer rules.
# OkHttp (used by Ktor) references optional TLS providers that are not shipped.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
