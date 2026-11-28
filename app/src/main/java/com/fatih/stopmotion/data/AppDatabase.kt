package com.fatih.stopmotion.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, FrameEntity::class, AudioTrackEntity::class, LayerEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun frameDao(): FrameDao
    abstract fun audioDao(): AudioDao
    abstract fun layerDao(): LayerDao

    companion object {
        /** Tahap 2: menambah pengaturan onion skin per proyek. Data lama tetap aman. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN onionEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE projects ADD COLUMN onionOpacity REAL NOT NULL DEFAULT 0.5")
                db.execSQL("ALTER TABLE projects ADD COLUMN onionFrameCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE projects ADD COLUMN onionColor INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Tahap 3: hapus lunak frame untuk undo/redo. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE frames ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Tahap 4: efek proyek + tabel track audio. Data lama tetap aman. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN fxFilter INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN fxIntensity REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE projects ADD COLUMN fxVignette REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE projects ADD COLUMN fxFadeIn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN fxFadeOut INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN fxFadeWhite INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audio_tracks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`projectId` INTEGER NOT NULL, `name` TEXT NOT NULL, `filePath` TEXT NOT NULL, " +
                        "`kind` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, " +
                        "`trimStartMs` INTEGER NOT NULL, `trimEndMs` INTEGER NOT NULL, `volume` REAL NOT NULL, " +
                        "`fadeInMs` INTEGER NOT NULL, `fadeOutMs` INTEGER NOT NULL, `muted` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_tracks_projectId` ON `audio_tracks` (`projectId`)")
            }
        }

        /** Tahap 7: penghapus latar (green/blue screen). Data lama tetap aman. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN chromaMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN chromaTolerance REAL NOT NULL DEFAULT 0.4")
                db.execSQL("ALTER TABLE projects ADD COLUMN chromaSoftness REAL NOT NULL DEFAULT 0.3")
                db.execSQL("ALTER TABLE projects ADD COLUMN chromaBgMode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN chromaBgPath TEXT")
            }
        }

        /** Tahap 8: coretan erase manual per frame. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE frames ADD COLUMN eraseData TEXT")
            }
        }

        /** Tahap layer: tabel layers (gambar/klip video di atas foto). */
        /** Tahap 9.1: keyframe animasi layer. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE layers ADD COLUMN keyframes TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `layers` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `projectId` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `kind` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `frameDir` TEXT, " +
                        "`frameCount` INTEGER NOT NULL, `frameRate` REAL NOT NULL, `srcWidth` INTEGER NOT NULL, " +
                        "`srcHeight` INTEGER NOT NULL, `zOrder` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `loopClip` INTEGER NOT NULL, `posX` REAL NOT NULL, " +
                        "`posY` REAL NOT NULL, `scale` REAL NOT NULL, `rotation` REAL NOT NULL, `opacity` REAL NOT NULL, " +
                        "`chromaMode` INTEGER NOT NULL, `chromaTolerance` REAL NOT NULL, `chromaSoftness` REAL NOT NULL, " +
                        "`visible` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_layers_projectId` ON `layers` (`projectId`)")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "fatih_stopmotion.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()
    }
}
