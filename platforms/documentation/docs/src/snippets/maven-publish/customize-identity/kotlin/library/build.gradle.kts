plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenCentral()
}

publishing {
    repositories {
        maven {
            // change to point to your repo, e.g. http://my.org/repo
            url = uri(rootProject.layout.buildDirectory.dir("repo"))
        }
    }
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.17")
}

// tag::customize-identity[]
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.gradle.sample"
            artifactId = "library"
            version = "1.1"

            from(components["java"])
        }
    }
}
// end::customize-identity[]
