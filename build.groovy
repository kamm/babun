#!/usr/bin/env groovy
import static java.lang.System.*
import static java.lang.System.getenv

VERSION = new File("${getRoot()}/babun.version").text.trim()
BUILD=new Date().format('yyyy-MM-dd')

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
        executeBabunCygwin()
    } else if (mode == "cleandist"){
        doPartClean()
		doPackage()
	}
    
    log "FINISHED"
}

def checkArguments() {
    if (this.args.length != 1 || !this.args[0].matches("clean|partclean|packages|cygwin|core|dist|package|release|cleandist")) {
        err.println "Usage: build.groovy <clean|partclean|packages|cygwin|core|dist|package|release|cleandist>"
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
        if (!(new File(target,"babun-cygwin")).deleteDir()) {
            throw new RuntimeException("Cannot delete target/babun-core folder")
        }
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

def executeBabunPackages(){
	File confFolder = new File(getRoot(), "babun-packages/conf/")
	File outputFolder = new File(getTarget(), "babun-packages")
	outputFolder.mkdirs()
    String setupVersion
    try {
        downloadPackages(confFolder, outputFolder, "x86_64")
        copyPackagesListToTarget(confFolder, outputFolder, "x86_64")
    } catch (Exception ex) {
        log("Unexpected error occurred: " + ex + " . Quitting!")
        ex.printStackTrace()
        exit(-1)
    }
}

def downloadPackages(File confFolder, File outputFolder, String bitVersion) {
    File packagesFile = new File(confFolder, "cygwin.${bitVersion}.packages")
    def rootPackages = packagesFile.readLines().findAll() { it }
    def repositories = new File(confFolder, "cygwin.repositories").readLines().findAll() { it }
    def processed = [] as Set
    for (repo in repositories) {
        String setupIni = downloadSetupIni(repo, bitVersion, outputFolder)
        new File(outputFolder,"cygwin.version").withWriter('utf-8') {
            writer -> writer.writeLine(getCygwinVersion(setupIni))
        }
        for (String rootPkg : rootPackages) {
            if (processed.contains(rootPkg.trim())) continue
            def processedInStep = downloadRootPackage(repo, setupIni, rootPkg.trim(), processed, outputFolder)
            processed.addAll(processedInStep)
        }
        rootPackages.removeAll(processed)
        if (rootPackages.isEmpty()) {
            return
        }
    }
    if (!rootPackages.isEmpty()) {
        error("Could not download the following ${rootPackages}! Quitting!")
        exit(-1)
    }
}

def downloadSetupIni(String repository, String bitVersion, File outputFolder) {
    log "Downloading [setup.ini] from repository [${repository}]"
    String setupIniUrl = "${repository}/${bitVersion}/setup.ini"
    
	
	File outputFile = new File(new File(outputFolder, "${repository.split("/")[2]}/${bitVersion}"), "setup.ini")
	outputFile.getParentFile().mkdirs()
	outputFile.createNewFile()
	use(FileBinaryCategory) {
		outputFile << setupIniUrl.toURL()
	}
    
    return outputFile.text
}

def downloadRootPackage(String repo, String setupIni, String rootPkg, Set<String> processed, File outputFolder) {
    def processedInStep = [] as Set
    log "Processing top-level package [$rootPkg]"
    def packagesToProcess = [] as Set
    try {
        buildPackageDependencyTree(setupIni, rootPkg, packagesToProcess)
        for (String pkg : packagesToProcess) {
            if (processed.contains(pkg) || processedInStep.contains(pkg)) continue
            String pkgInfo = parsePackageInfo(setupIni, pkg)
            String pkgPath = parsePackagePath(pkgInfo)
            if (pkgPath) {
                log "  Downloading package [$pkg]"
                if (downloadPackage(repo, pkgPath, outputFolder)) {
                    processedInStep.add(pkg)
                }
            } else if (pkgInfo) {
                // packages doesn't have binary file
                processedInStep.add(pkg)
            } else {
                log "  Cannot find package [$pkg] in the repository"
                processedInStep = [] as Set // reset as the tree could not be fetched
                break;
            }
        }
    } catch (Exception ex) {
        error("Could not download dependency tree for [$rootPkg]")
        ex.printStackTrace()
        processedInStep = [] as Set
    }
    processedInStep
}

def buildPackageDependencyTree(String setupIni, String pkgName, Set<String> result) {
    String pkgInfo = parsePackageInfo(setupIni, pkgName)

    if (!pkgInfo) {
                pkgName=pkgName.replace("perl5_026","perl5_032")
        pkgInfo = setupIni?.split("(?=@ )")?.find() { it.contains("provides: ${pkgName}") }
        if(!pkgInfo){
            throw new RuntimeException("Cannot find dependencies of [${pkgName}]")
        }
        log "  Parsing virtual package [${pkgName}]"
        String newPkgName=parsePackageName(pkgInfo);
        log "    parsed as [${newPkgName}]"
        result.add(newPkgName)

    }else{
        result.add(pkgName)
    }
    String[] deps = parsePackageRequires(pkgInfo)
    for (String dep : deps) {
        if (!result.contains(dep.trim())) {
            buildPackageDependencyTree(setupIni, dep.trim(), result)
        }
    }
}

def parsePackageRequires(String pkgInfo) {
    String requires = pkgInfo?.split("\n")?.find() { it.startsWith("depends2:") }
    return requires?.replace("depends2:", "")?.replace(",","")?.replace(" _windows ( >= 6.1 )","")?.replace(" _windows ( >= 6.3 )","")?.trim()?.split("\\s")
}

def getCygwinVersion(String setupIni){
    String version = setupIni?.split("(?=@)")?.find(){it.startsWith("@ cygwin")}.split("\n").find(){it.startsWith("version: ")}.replace("version:","").trim();
    return version
}

def parsePackageName(String pkgInfo) {
    String name = pkgInfo?.split("\n")?.find() { it.startsWith("@ ") }
    return name?.replace("@ ", "")?.trim()
}

def parsePackageInfo(String setupIni, String packageName) {
    return setupIni?.split("(?=@ )")?.find() { it.contains("@ ${packageName}") }
}

def parsePackagePath(String pkgInfo) {
    String version = pkgInfo?.split("\n")?.find() { it.startsWith("install:") }
    String[] tokens = version?.replace("install:", "")?.trim()?.split("\\s")
    return tokens?.length > 0 ? tokens[0] : null
}

def downloadPackage(String repositoryUrl, String packagePath, File outputFolder) {
    String packageUrl = repositoryUrl + packagePath
	String repoName = packageUrl.split("/")[2]
	String outputPath = "${outputFolder.getAbsolutePath()}/${repoName}/${packagePath}"
	downloadFile(packageUrl, new File(outputPath), false)
    return true
}

def copyPackagesListToTarget(File confFolder, File outputFolder, String bitVersion) {
    File packagesFile = new File(confFolder, "cygwin.${bitVersion}.packages")
    File outputFile = new File(outputFolder, "cygwin.${bitVersion}.packages")
    outputFile.createNewFile()
    outputFile << packagesFile.text
}

def downloadCygwinInstaller(File outputFolder) {    
    File cygwinInstaller = new File(outputFolder, "setup-x86_64.exe")
    if(!cygwinInstaller.exists()) {
        println "Downloading Cygwin installer"
        use(FileBinaryCategory) {
            cygwinInstaller << "http://cygwin.com/setup-x86_64.exe".toURL()
        }
    } else {
        println "Cygwin installer alread exists, skipping the download!";
    }

    return cygwinInstaller
}

def installCygwin(File cygwinInstaller, File repoFolder, File cygwinFolder, File pkgsFile) {    
    err.println "Installing cygwin"
    String pkgs = pkgsFile.text.trim().replaceAll("(\\s)+", ",")    
    err.println "Packages to install: ${pkgs}"
    String installCommand = "\"${cygwinInstaller.absolutePath}\" " +
            "--quiet-mode " +
            "--local-install " +
            "--local-package-dir \"${repoFolder.absolutePath}\" " +
            "--root \"${cygwinFolder.absolutePath}\" " +
            "--no-shortcuts " +
            "--no-startmenu " +
            "--no-desktop " +
            "--packages " + pkgs
    err.println installCommand
    executeCmd(installCommand)
}


def executeBabunCygwin(boolean downloadOnly = false){
	File repoFolder = new File(getTarget(), "babun-packages")
	File inputFolder = new File(getRoot(), "babun-cygwin")
	File outputFolder = new File(getTarget(), "babun-cygwin")
	outputFolder.mkdirs()
    File cygwinFolder = new File(outputFolder, "cygwin")
	cygwinFolder.mkdirs()
	
	pkgsFile = new File(getRoot(), "babun-packages/conf/cygwin.x86_64.packages")
    try {
        
        File cygwinInstaller = downloadCygwinInstaller(outputFolder)
        if(downloadOnly) {
            println "downloadOnly flag set to true - Cygwin installation skipped.";
            return
        }
        installCygwin(cygwinInstaller, repoFolder, cygwinFolder, pkgsFile)
        //cygwinInstaller.delete()

        // handle symlinks
        copySymlinksScripts(inputFolder, cygwinFolder)
        findSymlinks(cygwinFolder)
        new AntBuilder().copy( 
            file:"${repoFolder.absolutePath}/cygwin.version", 
            tofile:"${outputFolder.absolutePath}/cygwin/usr/local/etc/babun/installed/cygwin" 
        )
    } catch (Exception ex) {
        error("ERROR: Unexpected error occurred: " + ex + " . Quitting!", true)
        ex.printStackTrace()
        exit(-1)
    }
}

def copySymlinksScripts(File inputFolder, File cygwinFolder) {
	new AntBuilder().copy(todir: "${cygwinFolder.absolutePath}/etc/postinstall", quiet: true) {
        fileset(dir: "${inputFolder.absolutePath}/symlinks", defaultexcludes:"no")
    }    
}

def findSymlinks(File cygwinFolder) {
    String symlinksFindScript = "/etc/postinstall/symlinks_find.sh"
    String findSymlinksCmd = "${cygwinFolder.absolutePath}/bin/bash.exe --norc --noprofile \"${symlinksFindScript}\""
    executeCmd(findSymlinksCmd)
    new File(cygwinFolder, symlinksFindScript).renameTo(new File(cygwinFolder, symlinksFindScript + ".done"))
}

def copyCygwin(File cygwinFolder, File outputFolder) {
    println "Copying ${cygwinFolder.absolutePath} to ${outputFolder.absolutePath}/cygwin"
    new AntBuilder().copy( todir: "${outputFolder.absolutePath}/cygwin", quiet: true ) {
      fileset( dir: "${cygwinFolder.absolutePath}", defaultexcludes:"no" )
    }
    println "Copying ${cygwinFolder.absolutePath} to ${outputFolder.absolutePath}/cygwin done"
}

def copyCygwinForDist(File cygwinFolder, File outputFolder) {
    println "Copying ${cygwinFolder.absolutePath} to ${outputFolder.absolutePath}/.babun/cygwin"
    new AntBuilder().copy(todir: "${outputFolder.absolutePath}/.babun/cygwin", quiet: false) {
        fileset(dir: "${cygwinFolder.absolutePath}", defaultexcludes:"no") {
            exclude(name: "Cygwin.bat")
            exclude(name: "Cygwin.ico")
            exclude(name: "Cygwin-Terminal.ico")
        }
    }
}

def repairSymlinks(File outputFolder) {
    String symlinksRepairScript = "/etc/postinstall/symlinks_repair.sh"
    String fixBOM = "${outputFolder.absolutePath}/cygwin/bin/bash.exe --norc --noprofile -c \"/bin/sed -i '1s/^\\xEF\\xBB\\xBF//' ${symlinksRepairScript}\""
    String repairSymlinksCmd = "${outputFolder.absolutePath}/cygwin/bin/bash.exe --norc --noprofile \"${symlinksRepairScript}\""
    executeCmd(fixBOM, true)
    executeCmd(repairSymlinksCmd, true)
}

def executeBabunCore(){
	File outputFolder = new File(getTarget(), "babun-core")
    copyCygwin(new File(getTarget(), "babun-cygwin/cygwin"), outputFolder)
    
    String bash = "${outputFolder.absolutePath}/cygwin/bin/bash.exe -l"
    String dash = "${outputFolder.absolutePath}/cygwin/bin/dash.exe"
    
    // rebase dll's
    executeCmd("${dash} -c '/usr/bin/rebaseall'")
    repairSymlinks(outputFolder)   

	File babunSourceDest = new File(outputFolder, "cygwin/usr/local/etc/babun/source")
	babunSourceDest.mkdirs()
    new AntBuilder().copy( todir: "${babunSourceDest.absolutePath}/babun-core", quiet: true ) {
      fileset( dir: "babun-core", defaultexcludes:"no" )
    }
    new AntBuilder().copy( todir: "${babunSourceDest.absolutePath}/babun-dist", quiet: true ) {
      fileset( dir: "babun-dist", defaultexcludes:"no" )
    }
	new AntBuilder().copy( file: "babun.version" ,todir: "${babunSourceDest.absolutePath}", quiet: true )
	
	File babunBuild = new File(babunSourceDest, "babun.build")
	babunBuild.createNewFile()
	babunBuild << BUILD
	
    
    // checkout babun
    //String sslVerify = "git config --global http.sslverify"
    //String src = "/usr/local/etc/babun/source"
    //String clone = "git clone https://github.com/kamm/babun.git ${src}"
    //String babunBranch = getenv("babun_branch") ? getenv("babun_branch") : "master"
    //String checkout = "git --git-dir='${src}/.git' --work-tree='${src}' checkout ${babunBranch}"    
    //executeCmd("${bash} -c \"mkdir -p /usr/local/bin\"")
    //executeCmd("${bash} -c \"mkdir -p /usr/local/etc/babun\"")
    //executeCmd("${bash} -c \"${sslVerify} 'false'\"")
    //executeCmd("${bash} -c \"${clone}\"")
    //executeCmd("${bash} -c \"${checkout}\"")
    //executeCmd("${bash} -c \"${sslVerify} 'true'\"")
    
    // remove windows new line feeds
    String dos2unix = "find /usr/local/etc/babun/source/babun-core -type f -exec dos2unix {} \\;"
    executeCmd("${bash} -c \"${dos2unix}\"", true)

    // make installer executable
    String chmod = "find /usr/local/etc/babun/source/babun-core -type f -regex '.*sh' -exec chmod u+x {} \\;"
    executeCmd("${bash} -c \"${chmod}\"")

    // invoke init.sh
    executeCmd("${bash} -c \"/usr/local/etc/babun/source/babun-core/tools/init.sh x86_64\"")

    // run babun installer - yay!
    executeCmd("${bash} -c \"/usr/local/etc/babun/source/babun-core/plugins/install.sh\"")
}

def executeBabunDist(){
	File cygwinFolder = new File(getTarget(), "babun-core/cygwin")
	File inputFolder  = new File(getRoot(), "babun-dist")
	File outputFolder = new File(getTarget(), "babun-dist")

	copyCygwinForDist(cygwinFolder, outputFolder)
    copyTools(inputFolder, outputFolder)
    copyStartScripts(inputFolder, outputFolder)
    copyFonts(inputFolder, outputFolder)
    // prepare Dist
    zipBabun(outputFolder)
    copyInstallScripts(inputFolder, outputFolder)
    createBabunDist(inputFolder, outputFolder, VERSION)   
}

def copyTools(File inputFolder, File outputFolder) {
    println "Copying ${inputFolder.absolutePath}/tools to ${outputFolder.absolutePath}/.babun/tools"
    new AntBuilder().copy(todir: "${outputFolder.absolutePath}/.babun/tools", quiet: true) {
        fileset(dir: "${inputFolder.absolutePath}/tools", defaultexcludes:"no")
    }
}

def copyFonts(File inputFolder, File outputFolder) {
    println "Copying ${inputFolder.absolutePath}/fonts to ${outputFolder.absolutePath}/.babun/fonts"
    new AntBuilder().copy(todir: "${outputFolder.absolutePath}/.babun/fonts", quiet: true) {
        fileset(dir: "${inputFolder.absolutePath}/fonts", defaultexcludes:"no")
    }
}

def copyStartScripts(File inputFolder, File outputFolder) {
    println "Copying ${inputFolder.absolutePath}/start to ${outputFolder.absolutePath}/.babun"
    new AntBuilder().copy(todir: "${outputFolder.absolutePath}/.babun", quiet: true) {
        fileset(dir: "${inputFolder.absolutePath}/start", defaultexcludes:"no")
    }
}

def zipBabun(File outputFolder) {
    /*new AntBuilder().zip(destFile: "${outputFolder.absolutePath}/dist/dist/babun.zip", level: 9) {
        fileset(dir: "${outputFolder.absolutePath}", defaultexcludes:"no") {
            include(name: '.babun/**')
        }
    }*/
	//7z a -t7z Files.7z -mx9 -aoa
	def command = "babun-dist\\7zip-64\\7z.exe a -t7z ${outputFolder.absolutePath}\\dist\\dist\\babun.7z -mx9 ${outputFolder.absolutePath}\\.babun"
	executeCmd(command, true)
}

def copyInstallScripts(File inputFolder, File outputFolder) {
	new AntBuilder().copy(todir: "${outputFolder.absolutePath}/dist/dist", quiet: true) {
        fileset(dir: "${inputFolder.absolutePath}/7zip", defaultexcludes:"no") { include(name: "7z.*") }        
    }
    new AntBuilder().copy(todir: "${outputFolder.absolutePath}/dist/dist", quiet: true) {
        fileset(dir: "${inputFolder.absolutePath}/tools", defaultexcludes:"no") { include(name: "freespace.vbs") }
    }    
    new AntBuilder().copy(todir: "${outputFolder.absolutePath}/dist", quiet: true) {
        fileset(dir: "${inputFolder.absolutePath}/install", defaultexcludes:"no") { include(name: "install.*") }
    }
}

def createBabunDist(File inputFolder, File outputFolder, String version) {
    // rename dist folder
    File dist = new File(outputFolder, "dist")
    File distWithVersion = new File(outputFolder, "babun-${version}")
    dist.renameTo(distWithVersion)
	
	
	def zipCommand = "babun-dist\\7zip-64\\7z.exe a -t7z ${outputFolder.absolutePath}\\babun.7z -mx0 ${outputFolder.absolutePath}\\babun-${version}"
	executeCmd(zipCommand, true)
    generateConfigTxt(inputFolder, outputFolder, version)
	def exeCommand = "cmd /c copy /y/b ${inputFolder.absolutePath}\\sfx\\7zsd.sfx+${outputFolder.absolutePath}\\sfx\\config.txt+${outputFolder.absolutePath}\\babun.7z ${outputFolder.absolutePath}\\babun-${version}-${BUILD}.exe"
	executeCmd(exeCommand, true)
}

def generateConfigTxt(File inputFolder, File outputFolder, String version){
    File inputFile = new File(inputFolder, "sfx/config.txt")
    input = inputFile.text
    println input
    output = input.replace("%VERSION%", version)
    println output
    File outputFile = new File(outputFolder, "sfx/config.txt")
    outputFile.getParentFile().mkdirs()
    outputFile.withWriter('utf-8'){
        writer -> writer.write output
    }
}

def executeRelease() {
    log "EXEC release"
    assert getenv("bintray_user") != null
    assert getenv("bintray_secret") != null
    File artifact = new File(getTarget(), "babun-dist/babun-${VERSION}-dist.zip")
    def args = ["groovy", "babun-dist/release/release.groovy", "babun", "babun-dist", VERSION,
            artifact.absolutePath, getenv("bintray_user"), getenv("bintray_secret")]
    executeCmd(args, getRoot())
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

def executeCmd(List<String> command, File workingDir) {
    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(workingDir)
    Process process = processBuilder.start()
    addShutdownHook { process.destroy() }
    process.consumeProcessOutput(err, err)
    process.waitForProcessOutput()
    assert process.exitValue() == 0
}

def executeCmd(String command, boolean disableOutput = false) {
    log command
    def process = command.execute()
    addShutdownHook { process.destroy() }
    if(!disableOutput){
        process.consumeProcessOutput(out, out)
    }
    process.waitForProcessOutput()
    return process.exitValue()
}


def getReleaseScript() {
    new File(getRoot(), "release.groovy")
}

def log(String msg) {
    println "[${new Date()}] ${msg}"
}

def error(String message, boolean noPrefix = false) {
    log((noPrefix ? "" : "ERROR: ") + message)
}


def downloadFile(String url, File output, boolean overwrite = false){
    if(!output.exists() || overwrite) {
        try{
			log "Downloading ${url}"
			output.getParentFile().mkdirs()
			use(FileBinaryCategory) {
				output << url.toURL()
			}
		}
		catch (Exception ex) {
			return false
		}
    } 
    return true
}

class FileBinaryCategory {
    def static leftShift(File file, URL url) {
        url.withInputStream { is ->
            file.withOutputStream { os ->
                def bs = new BufferedOutputStream(os)
                bs << is
            }
        }
    }
}