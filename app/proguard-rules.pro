# Project-specific R8 rules only.
#
# Room, Google Play Services, Maps, DataStore and kotlinx-coroutines ship their own consumer
# rules, so blanket `-keep` rules for their packages only make the APK bigger and slower.

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
