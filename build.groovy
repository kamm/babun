#!/usr/bin/env groovy
import static java.lang.System.*
import static java.lang.System.getenv

VERSION = new File("${getRoot()}/babun.version").text.trim()
TEN_MINUTES = 1000
TWENTY_MINUTES = 2000

execute()

def execute() {
    log "EXEC"
    checkArguments()
    String mode = this.args[0]
    if (mode == "clean") {
        doClean()
    } else if (mode == "partclean") {
        doPartClean()
    } else if (mode == "cygwin") {
        executeBabunCygwin()
    } else if (mode == "package") {
        doPackage()
    } else if (mode == "release") {
        doRelease()
    } else if (mode == "dist") {
        executeBabunDist()
    } else if (mode == "core") {
        executeBabunCore()
    } else if (mode == "packages") {
        executeBabunPackages()
    } else if (mode == "cygwin") {
        executeBabunCore()
    }
    
    log "FINISHED"
}

def checkArguments() {
    if (this.args.length != 1 || !this.args[0].matches("clean|partclean|packages|cygwin|core|dist|package|release")) {
        err.println "Usage: build.groovy <clean|partclean|packages|cygwin|core|dist|package|release>"
        exit(-1)
    }
}

def initEnvironment() {
    File target = getTarget()
    if (!target.exists()) {
        target.mkdir()
    }
}

def doPartClean() {
    log "EXEC partclean"
    File target = getTarget()
    if (target.exists()) {
        if (!(new File(target,"babun-core")).deleteDir()) {
            throw new RuntimeException("Cannot delete target/babun-core folder")
        }
        if (!(new File(target,"babun-dist")).deleteDir()) {
            throw new RuntimeException("Cannot delete target/babun-dist folder")
        }
    }
}

def doClean() {
    log "EXEC clean"
    File target = getTarget()
    if (target.exists()) {
        if (!target.deleteDir()) {
            throw new RuntimeException("Cannot delete target folder")
        }
    }
}

def doPackage() {
    log "EXEC package"  
    executeBabunPackages()  
    executeBabunCygwin()
    executeBabunCore()
    executeBabunDist()
}

def doRelease() {
    log "EXEC release"
    doPackage()
    executeRelease()
}

def executeBabunPackages() {    
    String module = "babun-packages"
    log "EXEC ${module}"
    if (shouldSkipModule(module)) return
    File workingDir = new File(getRoot(), module);
    String conf = new File(getRoot(), "${module}/conf/").absolutePath
    String out = new File(getTarget(), "${module}").absolutePath
    def command = ["groovy.bat", "packages.groovy", conf, out]
    executeCmd(command, workingDir, TEN_MINUTES)
}

def executeBabunCygwin(boolean downloadOnly = false) {
    String module = "babun-cygwin"
    log "EXEC ${module}"
    if (shouldSkipModule(module)) return
    File workingDir = new File(getRoot(), module);
    String input = workingDir.absolutePath
    String repo = new File(getTarget(), "babun-packages").absolutePath
    String out = new File(getTarget(), "${module}").absolutePath
    String pkgs = new File(getRoot(), "babun-packages/conf/cygwin.x86.packages")
    String downOnly = downloadOnly as String
    println "Download only flag set to: ${downOnly}"
    def command = ["groovy.bat", "cygwin.groovy", repo, input, out, pkgs, downOnly]
    executeCmd(command, workingDir, TEN_MINUTES)
}

def executeBabunCore() {
    String module = "babun-core"
    log "EXEC ${module}"
    if (shouldSkipModule(module)) return
    File workingDir = new File(getRoot(), module);
    String root = getRoot().absolutePath
    String cygwin = new File(getTarget(), "babun-cygwin/cygwin").absolutePath
    String out = new File(getTarget(), "${module}").absolutePath    
    String branch = getenv("babun_branch") ? getenv("babun_branch") : "master"
    println "Taking babun branch [${branch}]"
    def command = ["groovy.bat", "core.groovy", root, cygwin, out, branch]
    executeCmd(command, workingDir, TEN_MINUTES)
}

def executeBabunDist() {
    String module = "babun-dist"
    log "EXEC ${module}"
    if (shouldSkipModule(module)) return
    File workingDir = new File(getRoot(), module);
    String input = workingDir.absolutePath
    String cygwin = new File(getTarget(), "babun-core/cygwin").absolutePath
    String out = new File(getTarget(), "${module}").absolutePath
    def command = ["groovy.bat", "dist.groovy", cygwin, input, out, VERSION]
    executeCmd(command, workingDir, TEN_MINUTES)
}

def executeRelease() {
    log "EXEC release"
    assert getenv("bintray_user") != null
    assert getenv("bintray_secret") != null
    File artifact = new File(getTarget(), "babun-dist/babun-${VERSION}-dist.zip")
    def args = ["groovy", "babun-dist/release/release.groovy", "babun", "babun-dist", VERSION,
            artifact.absolutePath, getenv("bintray_user"), getenv("bintray_secret")]
    executeCmd(args, getRoot(), TWENTY_MINUTES)
}

def shouldSkipModule(String module) {
    File out = new File(getTarget(), module)
    log "Checking if skip module ${module} -> folder ${out.absolutePath}"
    if (out.exists()) {
        log "SKIP ${module}"
        return true
    }
    log "DO NOT SKIP ${module}"
    return false
}

File getTarget() {
    return new File(getRoot(), "target")
}

File getRoot() {
    return new File(getClass().protectionDomain.codeSource.location.path).parentFile
}

def executeCmd(List<String> command, File workingDir, int timeout) {
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(workingDir)
    Process process = processBuilder.start()
    addShutdownHook { process.destroy() }
    process.consumeProcessOutput(out, err)
    process.waitForOrKill(timeout*1000*60*10)
    assert process.exitValue() == 0
}

def getReleaseScript() {
    new File(getRoot(), "release.groovy")
}

def log(String msg) {
    println "[${new Date()}] ${msg}"
}