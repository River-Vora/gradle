package model

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import common.Arch
import common.Os
import common.VersionedSettingsBranch
import configurations.ParallelizationMethod
import java.io.File
import java.util.LinkedList

const val MASTER_CHECK_CONFIGURATION = "Gradle_Master_Check"
const val MAX_PROJECT_NUMBER_IN_BUCKET = 11

/**
 * Process test-class-data.json and generates test-buckets.json
 *
 * Usage: `mvn compile exec:java@update-test-buckets -DinputTestClassDataJson=/path/to/test-class-data.json`.
 * You can get the JSON file as an artifacts of the "autoUpdateTestSplitJsonOnGradleMaster" pipeline in TeamCity.
 */
fun main() {
    val model =
        CIBuildModel(
            projectId = "Check",
            branch = VersionedSettingsBranch("master"),
            buildScanTags = listOf("Check"),
            subprojects = JsonBasedGradleSubprojectProvider(File("./subprojects.json")),
        )
    val testClassDataJson = File(System.getProperty("inputTestClassDataJson") ?: throw IllegalArgumentException("Input file not found!"))
    val generatedBucketsJson = File(System.getProperty("outputBucketSplitJson", "./test-buckets.json"))

    FunctionalTestBucketGenerator(model, testClassDataJson).generate(generatedBucketsJson)
}

class FunctionalTestBucketGenerator(
    private val model: CIBuildModel,
    testTimeDataJson: File,
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val buckets: Map<TestCoverage, List<SmallSubprojectBucket>> = buildBuckets(testTimeDataJson, model)

    fun generate(jsonFile: File) {
        val output =
            buckets.map {
                TestCoverageAndBucketSplits(it.key.uuid, it.value.map { it.toJsonBucket() })
            }
        jsonFile.writeText(gson.toJson(output))
    }

    private fun buildBuckets(
        buildClassTimeJson: File,
        model: CIBuildModel,
    ): Map<TestCoverage, List<SmallSubprojectBucket>> {
        val sType = object : TypeToken<BuildProjectToSubprojectTestClassTimes>() {}.type
        val buildProjectClassTimes = gson.fromJson<BuildProjectToSubprojectTestClassTimes>(buildClassTimeJson.readText(), sType)

        val result = mutableMapOf<TestCoverage, List<SmallSubprojectBucket>>()
        for (stage in model.stages) {
            for (testCoverage in stage.functionalTests) {
                if (testCoverage.testType !in
                    listOf(TestType.ALL_VERSIONS_CROSS_VERSION, TestType.QUICK_FEEDBACK_CROSS_VERSION, TestType.SOAK)
                ) {
                    result[testCoverage] = splitBucketsByTestClassesForBuildProject(testCoverage, buildProjectClassTimes)
                }
            }
        }
        return result
    }

    private fun splitBucketsByTestClassesForBuildProject(
        testCoverage: TestCoverage,
        buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes,
    ): List<SmallSubprojectBucket> {
        val validSubprojects = model.subprojects.getSubprojectsForFunctionalTest(testCoverage)

        // Build project not found, don't split into buckets
        val subProjectToClassTimes: MutableMap<String, List<TestClassTime>> =
            determineSubProjectClassTimes(testCoverage, buildProjectClassTimes)?.toMutableMap()
                ?: return validSubprojects.map { SmallSubprojectBucket(it, ParallelizationMethod.None) }

        validSubprojects.forEach {
            if (!subProjectToClassTimes.containsKey(it.name)) {
                subProjectToClassTimes[it.name] = emptyList()
            }
        }

        val subProjectTestClassTimes: List<SubprojectTestClassTime> =
            subProjectToClassTimes
                .entries
                .filter { "UNKNOWN" != it.key }
                .filter { model.subprojects.getSubprojectByName(it.key) != null }
                .map {
                    SubprojectTestClassTime(
                        model.subprojects.getSubprojectByName(it.key)!!,
                        it.value.filter { tct ->
                            tct.sourceSet != "test"
                        },
                    )
                }.sortedBy { -it.totalTime }
                .filter { onlyNativeSubprojectsForIntelMacs(testCoverage, it.subProject.name) }

        return parallelize(subProjectTestClassTimes, testCoverage) { numberOfBatches ->
            when (testCoverage.os) {
                Os.LINUX -> ParallelizationMethod.TestDistribution
                Os.ALPINE -> ParallelizationMethod.TestDistributionAlpine
                else -> ParallelizationMethod.TeamCityParallelTests(numberOfBatches)
            }
        }
    }

    private fun parallelize(
        subProjectTestClassTimes: List<SubprojectTestClassTime>,
        testCoverage: TestCoverage,
        parallelization: (Int) -> ParallelizationMethod,
    ): List<SmallSubprojectBucket> {
        // splitIntoBuckets() method expects us to split large element into N elements,
        // but we want to have a single bucket with N batches.
        // As a workaround, we repeat the bucket N times, and deduplicate the result at the end
        val resultIncludingDuplicates =
            splitIntoBuckets(
                LinkedList(subProjectTestClassTimes),
                SubprojectTestClassTime::totalTime,
                { largeElement, factor ->
                    List(factor) { SmallSubprojectBucket(largeElement.subProject, parallelization(factor)) }
                },
                { list ->
                    SmallSubprojectBucket(list.map { it.subProject }, parallelization(1))
                },
                testCoverage.expectedBucketNumber,
                MAX_PROJECT_NUMBER_IN_BUCKET,
            )
        return resultIncludingDuplicates.distinctBy { it.name }
    }

    private fun determineSubProjectClassTimes(
        testCoverage: TestCoverage,
        buildProjectClassTimes: BuildProjectToSubprojectTestClassTimes,
    ): Map<String, List<TestClassTime>>? {
        val testCoverageId = testCoverage.asId(MASTER_CHECK_CONFIGURATION)
        return buildProjectClassTimes[testCoverageId] ?: if (testCoverage.testType == TestType.SOAK) {
            null
        } else {
            val testCoverages = model.stages.flatMap { it.functionalTests }
            val foundTestCoverage =
                testCoverages.firstOrNull {
                    it.testType == TestType.PLATFORM &&
                        it.os == testCoverage.os &&
                        it.arch == testCoverage.arch &&
                        it.buildJvm == testCoverage.buildJvm
                }
            foundTestCoverage
                ?.let {
                    buildProjectClassTimes[it.asId(MASTER_CHECK_CONFIGURATION)]
                }?.also {
                    println(
                        "No test statistics found for ${testCoverage.asName()} (${testCoverage.uuid}), re-using the data from ${foundTestCoverage.asName()} (${foundTestCoverage.uuid})",
                    )
                }
        }
    }
}

fun onlyNativeSubprojectsForIntelMacs(
    testCoverage: TestCoverage,
    subprojectName: String,
): Boolean =
    if (testCoverage.os == Os.MACOS && testCoverage.arch == Arch.AMD64) {
        subprojectName.contains("native") ||
            // Include precondition-tester here so we understand that tests do run on macOS intel as well
            subprojectName in listOf("file-watching", "snapshots", "workers", "logging", "precondition-tester")
    } else {
        true
    }
