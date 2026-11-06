package io.github.mishkun.ataman

import com.intellij.ide.starter.ide.IdeProductProvider
import com.intellij.ide.starter.project.GitHubProject
import com.intellij.ide.starter.project.TestCaseTemplate

object UltimateCase : TestCaseTemplate(IdeProductProvider.IU) {

    val simpleProject = withProject(
        GitHubProject.fromGithub(
            branchName = "master",
            repoRelativeUrl = "Mishkun/Puerh.git"
        )
    )
}

object MyTestCase : TestCaseTemplate(IdeProductProvider.IC) {

    val simpleProject = withProject(
        GitHubProject.fromGithub(
            branchName = "master",
            repoRelativeUrl = "Mishkun/Puerh.git"
        )
    )
}
