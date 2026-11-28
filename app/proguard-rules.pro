# Aturan R8/ProGuard untuk build rilis.
# Room, Coil, CameraX, Compose, dan AndroidX membawa aturan konsumen sendiri, jadi tidak perlu aturan tambahan.

# Pertahankan nomor baris agar stack trace dari rilis bisa dibaca (dipetakan dengan mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
