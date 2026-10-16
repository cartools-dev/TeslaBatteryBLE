# Add project specific ProGuard rules here.

# cryptography-kotlin's JDK provider references BouncyCastle for an optional
# EC public-key derivation path. We never use it and BC is not bundled.
-dontwarn org.bouncycastle.jce.ECNamedCurveTable
-dontwarn org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
-dontwarn org.bouncycastle.math.ec.ECFieldElement
-dontwarn org.bouncycastle.math.ec.ECPoint
