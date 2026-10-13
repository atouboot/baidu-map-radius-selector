pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 国内镜像加速（可选，失败会自动回退到 mavenCentral）
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "BaiduMapRadiusSelector"
include(":app")
