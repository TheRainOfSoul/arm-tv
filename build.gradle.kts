import com.lagradost.cloudstream3.gradle.CloudstreamExtension
import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

buildscript {
    repositories {
        google()
        mavenCentral()
        // Плагин сборки CloudStream и часть зависимостей — с jitpack.
        maven("https://jitpack.io")
    }
    dependencies {
        // ВНИМАНИЕ по версиям: связка ниже — рабочая на момент написания.
        // Если сборка ругается, сверься с актуальным официальным шаблоном:
        //   https://github.com/recloudstream/cloudstream-extensions  (build.gradle.kts в корне)
        classpath("com.android.tools.build:gradle:8.7.3")
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        // Kotlin должен совпадать (или быть новее) с версией, которой собран stub CloudStream.
        // Сейчас cloudstream.jar имеет метаданные Kotlin 2.3.0 — как в официальном шаблоне
        // recloudstream/extensions. Со старым 2.0.21 компилятор не читал классы CloudStream.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

fun Project.cloudstream(configuration: CloudstreamExtension.() -> Unit) =
    extensions.getByName<CloudstreamExtension>("cloudstream").configuration()

fun Project.android(configuration: BaseExtension.() -> Unit) =
    extensions.getByName<BaseExtension>("android").configuration()

subprojects {
    apply(plugin = "com.android.library")
    apply(plugin = "kotlin-android")
    apply(plugin = "com.lagradost.cloudstream3.gradle")

    cloudstream {
        // Куда публикуется .cs3 (GitHub user / repo) — для генерации repo.json.
        setRepo("TheRainOfSoul", "arm-tv", "github")
    }

    android {
        compileSdkVersion(35)

        defaultConfig {
            minSdk = 23      // как договорились — охват реальных Android TV
            targetSdk = 35
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }

        // Начиная с Kotlin 2.2+ старый kotlinOptions{} — ошибка. Новый compilerOptions DSL
        // (как в официальном шаблоне recloudstream/extensions).
        tasks.withType<KotlinJvmCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_1_8)
                freeCompilerArgs.addAll(
                    "-Xno-call-assertions",
                    "-Xno-param-assertions",
                    "-Xno-receiver-assertions",
                )
            }
        }
    }

    dependencies {

        val cloudstream by configurations
        val implementation by configurations

        // Классы CloudStream (MainAPI, app, WebViewResolver, ExtractorLink…) —
        // только для компиляции, в .cs3 не пакуются (их даёт само приложение).
        // Конфигурация называется `cloudstream` (в старых версиях плагина была `apk`).
        cloudstream("com.lagradost:cloudstream3:pre-release")

        implementation(kotlin("stdlib"))
        implementation("com.github.Blatzar:NiceHttp:0.4.11")   // app.get/app.post
        implementation("org.jsoup:jsoup:1.18.1")               // парсинг HTML
    }
}
