#!/usr/bin/env groovy
import static java.lang.System.*
import static groovy.io.FileType.*
import groovy.time.*

setupIniMap = [:]

execute()
def elapsedTime(String module, Closure closure){
    def timeStart = new Date()
    println "${module} Starting at ${timeStart}"
    closure()
    def timeStop = new Date()
    println "${module} Started at ${timeStart}"
    println "${module} Finished at ${timeStop}"
    duration = TimeCategory.minus(timeStop, timeStart)
    println "${module} Took ${duration}"

}

def execute(){
    version = new File("${getRoot()}/babun.version").text.trim()
    bitVersion = "x86"
    
    File packagesInput  = new File(getRoot(), "babun-packages")
    File cygwinInput    = new File(getRoot(), "babun-cygwin")
    File distInput      = new File(getRoot(), "babun-dist")
    
    File packagesOutput = new File(getRoot(), "target/babun-packages")
    File cygwinOutput   = new File(getRoot(), "target/babun-cygwin")
    File coreOutput     = new File(getRoot(), "target/babun-core")
    File distOutput     = new File(getRoot(), "target/babun-dist")
    
    elapsedTime("[BABUN]"){
        elapsedTime("[PKGS]"){executePackages(packagesInput, packagesOutput, bitVersion)}
        elapsedTime("[CYGW]"){executeCygwin(packagesInput, packagesOutput, cygwinInput, cygwinOutput, bitVersion)}
        elapsedTime("[CORE]"){executeCore(cygwinOutput, coreOutput, "master")}
        elapsedTime("[DIST]"){executeDist(coreOutput, distInput, distOutput, version,bitVersion)}
    }

 
}

File getRoot() {
    return new File(getClass().protectionDomain.codeSource.location.path).parentFile
}


def executePackages(File packagesInput, File outputFolder, String bitVersion) {
    try {
        downloadPackages(packagesInput, outputFolder, "${bitVersion}")
        copyPackagesListToTarget(packagesInput, outputFolder, "${bitVersion}")
    } catch (Exception ex) {
        error("Unexpected error occurred: " + ex + " . Quitting!")
        ex.printStackTrace()
        exit(-1)
    }
}

def copyPackagesListToTarget(File packagesInput, File outputFolder, String bitVersion) {
    File packagesFile = new File(packagesInput, "conf/cygwin.${bitVersion}.packages")
    File outputFile = new File(outputFolder, "cygwin.${bitVersion}.packages")
    outputFile.createNewFile()
    outputFile << packagesFile.text
}

def downloadPackages(File packagesInput, File outputFolder, String bitVersion) {
    File packagesFile = new File(packagesInput, "conf/cygwin.${bitVersion}.packages")
    def rootPackages = packagesFile.readLines().findAll() { it }
    def repositories = new File(packagesInput, "conf/cygwin.repositories").readLines().findAll() { it }
    def processed = [] as Set
    for (repo in repositories) {
        String setupIni = downloadSetupIni(repo, bitVersion, outputFolder)
        parseSetupIni(setupIni)
        new File(outputFolder,"cygwin.version").withWriter('utf-8') { 
            writer -> writer.writeLine(getCygwinVersion(setupIni)) 
        } 
        for (String rootPkg : rootPackages) {
            if(rootPkg.trim().startsWith("#")) continue
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
    info("Downloading [setup.ini] from repository [${repository}]")
    String setupIniUrl = "${repository}/${bitVersion}/setup.ini"
    String setupIniContent = setupIniUrl.toURL().text
    String repoDomain = repository.replaceAll(/.*\/\/([a-z\.]+)\/.*/, '$1')
    File setupIni = new File(outputFolder,"${repoDomain}/${bitVersion}/setup.ini")
    setupIni.getParentFile().mkdirs()
    setupIni.withWriter{
        writer -> writer.print setupIniContent
    }

    return setupIniContent
}

def downloadRootPackage(String repo, String setupIni, String rootPkg, Set<String> processed, File outputFolder) {
    def processedInStep = [] as Set
    info("Processing top-level package [$rootPkg]")
    def packagesToProcess = [] as Set
    try {
        buildPackageDependencyTree(setupIni, rootPkg, packagesToProcess)
        for (String pkg : packagesToProcess) {
            if (processed.contains(pkg) || processedInStep.contains(pkg)) continue
            String pkgInfo = parsePackageInfo(setupIni, pkg)
            String pkgPath = parsePackagePath(pkgInfo)
            if (pkgPath) {
                info("  Downloading package [$pkg]")
                if (downloadPackage(repo, pkgPath, outputFolder)) {
                    processedInStep.add(pkg)
                }
            } else if (pkgInfo) {
                // packages doesn't have binary file
                processedInStep.add(pkg)
            } else {
                info("  Cannot find package [$pkg] in the repository")
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
		/*pkgName=pkgName.replace("perl5_026","perl5_032")
        pkgInfo = setupIni?.split("(?=@ )")?.find() { it.contains("provides: ${pkgName}") }
        if(!pkgInfo){
            throw new RuntimeException("Cannot find dependencies of [${pkgName}]")
        }
        info("  Parsing virtual package [${pkgName}]")
        String newPkgName=parsePackageName(pkgInfo);
        info("    parsed as [${newPkgName}]")*/
        String newPkgName = parseVirtualPackage(pkgName)
        info("  Parsing virtual package ${pkgName} as ${newPkgName}")
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

def parseVirtualPackage(String virtualPackage){
    if(virtualPackage == "perl5_026")
        return "perl_base"
    pkg = setupIniMap.find{it.value.contains("provides: ${virtualPackage}")}.key
    return pkg
}

def parsePackageRequires(String pkgInfo) {
    String requires = pkgInfo?.split("\n")?.find() { it.startsWith("requires:") }
    return requires?.replace("requires:", "")?.trim()?.split("\\s")
}

def getCygwinVersion(String setupIni){
    //String version = setupIni?.split("(?=@)")?.find(){it.startsWith("@ cygwin")}.split("\n").find(){it.startsWith("version: ")}.replace("version:","").trim();
    String version = setupIniMap["cygwin"].split("\n").find(){it.startsWith("version: ")}.replace("version:","").trim();
    return version
}

def parsePackageName(String pkgInfo) {
    String name = pkgInfo?.split("\n")?.find() { it.startsWith("@ ") }
    return name?.replace("@ ", "")?.trim()
}

def parseSetupIni(String setupIni){
    splitted = setupIni.split("(?=@)")
    println splitted.getClass()
    for(String packageInfo in splitted){
        setupIniMap[parsePackageName(packageInfo)]=packageInfo
    }
}

def parsePackageInfo(String setupIni, String packageName) {
    //return setupIni?.split("(?=@ )")?.find() { it.contains("@ ${packageName}") }
    return setupIniMap[packageName]
}

def parsePackagePath(String pkgInfo) {
    String version = pkgInfo?.split("\n")?.find() { it.startsWith("install:") }
    String[] tokens = version?.replace("install:", "")?.trim()?.split("\\s")
    return tokens?.length > 0 ? tokens[0] : null
}

def downloadPackage(String repositoryUrl, String packagePath, File outputFolder) {
    String packageUrl = repositoryUrl + packagePath
    String repoDomain = repositoryUrl.replaceAll(/.*\/\/([a-z\.]+)\/.*/, '$1')
    String outputPath = "${repoDomain}/${packagePath}"
    File outputFile = new File(outputFolder, outputPath)
    if(outputFile.exists()){
        info("    File ${packagePath} exists")
        return true;
    }
    outputFile.getParentFile().mkdirs()
    outputFile.withOutputStream{
        out ->
            new URL(packageUrl).withInputStream{ in -> out << in;}
    }
    return true
}

def error(String l){
    System.err.println(l)
}

def info(String l){
    System.err.println(l)
}

/////////////////CYGWIN PART
def executeCygwin(File packagesInput, File repoFolder, File inputFolder, File outputFolder, String bitVersion) {
    File pkgsFile = new File(packagesInput, "conf/cygwin.${bitVersion}.packages")
    File cygwinFolder
    boolean downloadOnly
    try {
        if (!outputFolder.exists()) {
            outputFolder.mkdir()
        }
        cygwinFolder = new File(outputFolder, "cygwin")
        cygwinFolder.mkdirs()
        // install cygwin
        File cygwinInstaller = downloadCygwinInstaller(outputFolder, bitVersion)
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
        error("ERROR: Unexpected error occurred: " + ex + " . Quitting!")
        ex.printStackTrace()
        exit(-1)
    }
}

def downloadCygwinInstaller(File outputFolder, String bitVersion) {    
    File cygwinInstaller = new File(outputFolder, "setup-${bitVersion}.exe")
    if(!cygwinInstaller.exists()) {
        println "Downloading Cygwin installer"
        cygwinInstaller.withOutputStream{
            out -> new URL( "http://cygwin.com/setup-${bitVersion}.exe").withInputStream{
                in-> out<<in
            }
        }
    } else {
        println "Cygwin installer alread exists, skipping the download!";
    }

    return cygwinInstaller
}

def installCygwin(File cygwinInstaller, File repoFolder, File cygwinFolder, File pkgsFile) {    
    println "Installing cygwin"
    String pkgs = pkgsFile.text.trim().replaceAll("(\\s)+", ",")    
    println "Packages to install: ${pkgs}"
    String installCommand = "\"${cygwinInstaller.absolutePath}\" " +
            "--quiet-mode " +
            "--local-install " +
            "--local-package-dir \"${repoFolder.absolutePath}\" " +
            "--root \"${cygwinFolder.absolutePath}\" " +
            "--no-shortcuts " +
            "--no-startmenu " +
            "--no-desktop " +
            "--packages " + pkgs
    println installCommand
    executeCmd(installCommand, 10)
}

def copySymlinksScripts(File inputFolder, File cygwinFolder) {
	new AntBuilder().copy(todir: "${cygwinFolder.absolutePath}/etc/postinstall", quiet: true) {
        fileset(dir: "${inputFolder.absolutePath}/symlinks", defaultexcludes:"no")
    }    
}

def findSymlinks(File cygwinFolder) {
    String symlinksFindScript = "/etc/postinstall/symlinks_find.sh"
    String findSymlinksCmd = "${cygwinFolder.absolutePath}/bin/bash.exe --norc --noprofile \"${symlinksFindScript}\""
    executeCmd(findSymlinksCmd, 100)
    new File(cygwinFolder, symlinksFindScript).renameTo(new File(cygwinFolder, symlinksFindScript + ".done"))
}

int executeCmd(String command, int timeout) {
    println "Executing: ${command}"
    def process = command.execute()
    addShutdownHook { process.destroy() }
    process.consumeProcessOutput(err, err)
    process.waitForProcessOutput()
    return process.exitValue()
}



//////CORE PART
def executeCore(File cygwinFolder, File outputFolder, String babunBranch) {
    try {
        copyCygwin(new File(cygwinFolder, "cygwin"), outputFolder, "cygwin")
        installCore(outputFolder, babunBranch)    
    } catch (Exception ex) {
        error("ERROR: Unexpected error occurred: " + ex + " . Quitting!")
        ex.printStackTrace()
        exit(-1)
    }
}

def repairSymlinks(File outputFolder) {
    String symlinksRepairScript = "/etc/postinstall/symlinks_repair.sh"
    String repairSymlinksCmd = "${outputFolder.absolutePath}/cygwin/bin/bash.exe --norc --noprofile \"${symlinksRepairScript}\""
    executeCmd(repairSymlinksCmd, 100)
}

// -----------------------------------------------------
// TODO - EXTERNALIZE THE INSTALLATION OF THE BABUN CORE
// THIS SHOULD BE A SEPARATE SHELL SCRIPT
// IT WILL ENABLE INSTALLING THE CORE ON OSX!!!
// -----------------------------------------------------
def installCore(File outputFolder, String babunBranch) {
    // rebase dll's
    executeCmd("${outputFolder.absolutePath}/cygwin/bin/dash.exe -c '/usr/bin/rebaseall'", 5)
	repairSymlinks(outputFolder)
    // setup bash invoked
    String bash = "${outputFolder.absolutePath}/cygwin/bin/bash.exe -l"

    // checkout babun
    String sslVerify = "git config --global http.sslverify"
    String src = "/usr/local/etc/babun/source"
    String clone = "git clone https://github.com/kamm/babun.git ${src}"
    String checkout = "git --git-dir='${src}/.git' --work-tree='${src}' checkout ${babunBranch}"
    executeCmd("${bash} -c \"mkdir -p /usr/local/bin\"",5)
    executeCmd("${bash} -c \"mkdir -p /usr/local/etc/babun\"",5)
    executeCmd("${bash} -c \"${sslVerify} 'false'\"",5)
    executeCmd("${bash} -c \"${clone}\"", 5)
    executeCmd("${bash} -c \"${checkout}\"", 5)
    executeCmd("${bash} -c \"${sslVerify} 'true'\"", 5)

    // remove windows new line feeds
    String dos2unix = "find /usr/local/etc/babun/source/babun-core -type f -exec dos2unix {} \\; >/dev/null 2>&1"
    executeCmd("${bash} -c \"${dos2unix}\"", 5)

    // make installer executable
    String chmod = "find /usr/local/etc/babun/source/babun-core -type f -regex '.*sh' -exec chmod u+x {} \\;"
    executeCmd("${bash} -c \"${chmod}\"", 5)

    // invoke init.sh
    executeCmd("${bash} \"/usr/local/etc/babun/source/babun-core/tools/init.sh\"", 5)

    // run babun installer - yay!
    executeCmd("${bash} \"/usr/local/etc/babun/source/babun-core/plugins/install.sh\"", 5)
}

////////////////DIST PART
def executeDist(File cygwinFolder, File inputFolder, File outputFolder, String version, String bitVersion) {
    try {
        // prepare .babun
        copyCygwin(new File(cygwinFolder,"cygwin"), outputFolder, ".babun/cygwin")
        copyTools(inputFolder, outputFolder)
        copyStartScripts(inputFolder, outputFolder)
        copyFonts(inputFolder, outputFolder)
        // prepare Dist
        zipBabun(inputFolder, outputFolder)
        copyInstallScripts(inputFolder, outputFolder)
        createBabunDist(inputFolder, outputFolder, version, bitVersion)   
    } catch (Exception ex) {
        error("ERROR: Unexpected error occurred: " + ex + " . Quitting!")
        ex.printStackTrace()
        exit(-1)
    }
}

def copyCygwin(File cygwinFolder, File outputFolder, String targetPath) {
    println "Copying ${cygwinFolder.absolutePath} to ${outputFolder.absolutePath}/.babun/cygwin"
    new AntBuilder().copy(todir: "${outputFolder.absolutePath}/${targetPath}", quiet: false) {
        fileset(dir: "${cygwinFolder.absolutePath}", defaultexcludes:"no") {
            exclude(name: "Cygwin.bat")
            exclude(name: "Cygwin.ico")
            exclude(name: "Cygwin-Terminal.ico")
        }
    }
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

def zipBabun(File inputFolder, File outputFolder) {
	def command = "${inputFolder.absolutePath}\\7zip-64\\7z.exe a -t7z ${outputFolder.absolutePath}\\dist\\dist\\babun.7z -mx9 ${outputFolder.absolutePath}\\.babun"
	executeCmd(command, 60)

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

def createBabunDist(File inputFolder, File outputFolder, String version, String bitVersion) {
    // rename dist folder
    File dist = new File(outputFolder, "dist")
    File distWithVersion = new File(outputFolder, "babun-${version}")
    dist.renameTo(distWithVersion)
	
	
	def zipCommand = "${inputFolder.absolutePath}\\7zip-64\\7z.exe a -t7z ${outputFolder.absolutePath}\\babun.7z -mx0 ${outputFolder.absolutePath}\\babun-${version}"
	executeCmd(zipCommand, 60)
    generateConfigTxt(inputFolder, outputFolder, version)
	def exeCommand = "cmd /c copy /y/b "+
        "${inputFolder.absolutePath}\\sfx\\7zsd.sfx+${outputFolder.absolutePath}\\sfx\\config.txt+${outputFolder.absolutePath}\\babun.7z "+
        "${outputFolder.absolutePath}\\babun-${version}-${bitVersion}.exe"
	executeCmd(exeCommand, 60)
}

def generateConfigTxt(File inputFolder, File outputFolder, String version){
    File inputFile = new File(inputFolder, "sfx/config.txt")
    input = inputFile.text
    output = input.replace("%VERSION%", version)
    File outputFile = new File(outputFolder, "sfx/config.txt")
    outputFile.getParentFile().mkdirs()
    outputFile.withWriter('utf-8'){
        writer -> writer.write output
    }
}


