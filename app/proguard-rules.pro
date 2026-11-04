# Default Proguard rules for Drift Stream

# A1: JNI entry point for jlibtorrent native bridge (D111)
-keep class com.frostwire.jlibtorrent.swig.libtorrent_jni { *; }

# A1: Native callback mentioned by libjlibtorrent.so and present in DEX
-keep class com.frostwire.jlibtorrent.swig.add_files_listener { *; }

# A1: Native callback mentioned by libjlibtorrent.so and present in DEX
-keep class com.frostwire.jlibtorrent.swig.alert_notify_callback { *; }

# A1: Native struct mentioned by libjlibtorrent.so and present in DEX
-keep class com.frostwire.jlibtorrent.swig.posix_stat_t { *; }

# A1: Native wrapper mentioned by libjlibtorrent.so and present in DEX
-keep class com.frostwire.jlibtorrent.swig.posix_wrapper { *; }

# A1: Native callback mentioned by libjlibtorrent.so and present in DEX
-keep class com.frostwire.jlibtorrent.swig.set_piece_hashes_listener { *; }

# A2: libVLC classes (libvlc-all-3.7.6.aar ships no consumer rules; R4)
-keep class org.videolan.libvlc.** { *; }

# A5: Retain line numbers and source file names for crash stack traces
-keepattributes SourceFile,LineNumberTable

# A5: Obfuscate the source file attribute to SourceFile
-renamesourcefileattribute SourceFile
