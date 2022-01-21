#!/usr/bin/env groovy
import static java.lang.System.*

execute()

def execute() {
    File cygwinFolder, inputFolder, outputFolder
    String version
    try {
        checkArguments()
        (cygwinFolder, inputFolder, outputFolder, version) = initEnvironment()
        // prepare .babun
        copyCygwin(cygwinFolder, outputFolder)
        copyTools(inputFolder, outputFolder)
        copyStartScripts(inputFolder, outputFolder)
        copyFonts(inputFolder, outputFolder)
        // prepare Dist
        zipBabun(outputFolder)
        copyInstallScripts(inputFolder, outputFolder)
        createBabunDist(inputFolder, outputFolder, version)        
    } catch (Exception ex) {
        error("ERROR: Unexpected error occurred: " + ex + " . Quitting!", true)
        ex.printStackTrace()
        exit(-1)
    }
}

def checkArguments() {
    if (this.args.length != 4) {
        error("Usage: dist.groovy <cygwin_folder> <input_folder> <output_folder> <version>")
        exit(-1)
    }
}

def initEnvironment() {
    File cygwinFolder = new File(this.args[0])
    File inputFolder = new File(this.args[1])
    File outputFolder = new File(this.args[2])
    String version = this.args[3] as String
    if (!outputFolder.exists()) {
        outputFolder.mkdir()
    }
    println "cygwinFolder: ${cygwinFolder}"
    println "inputFolder: ${inputFolder}"
    println "outputFolder: ${outputFolder}"
    println "version: ${version}"
    return [cygwinFolder, inputFolder, outputFolder, version]
}

def copyCygwin(File cygwinFolder, File outputFolder) {
    println "Copying ${cygwinFolder.absolutePath} to ${outputFolder.absolutePath}/.babun/cygwin"
    new AntBuilder().copy(todir: "${outputFolder.absolutePath}/.babun/cygwin", quiet: false) {
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

def zipBabun(File outputFolder) {
    /*new AntBuilder().zip(destFile: "${outputFolder.absolutePath}/dist/dist/babun.zip", level: 9) {
        fileset(dir: "${outputFolder.absolutePath}", defaultexcludes:"no") {
            include(name: '.babun/**')
        }
    }*/
	//7z a -t7z Files.7z -mx9 -aoa
	def command = "7zip-64/7z.exe a -t7z ${outputFolder.absolutePath}/dist/dist/babun.7z -mx9 ${outputFolder.absolutePath}/.babun"
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

def createBabunDist(File inputFolder, File outputFolder, String version) {
    // rename dist folder
    File dist = new File(outputFolder, "dist")
    File distWithVersion = new File(outputFolder, "babun-${version}")
    dist.renameTo(distWithVersion)
	
	
	def zipCommand = "7zip-64\\7z.exe a -t7z ${outputFolder.absolutePath}\\babun.7z -mx0 ${outputFolder.absolutePath}\\babun-${version}"
	executeCmd(zipCommand, 60)
	def exeCommand = "cmd /c copy /y/b ${inputFolder.absolutePath}\\sfx\\7zsd.sfx+${inputFolder.absolutePath}\\sfx\\config.txt+${outputFolder.absolutePath}\\babun.7z ${outputFolder.absolutePath}\\babun-${version}.exe"
	executeCmd(exeCommand, 60)
}

def error(String message, boolean noPrefix = false) {
    err.println((noPrefix ? "" : "ERROR: ") + message)
}

def executeCmd(String command, int timeout) {
    println "Executing: ${command}"
    def process = command.execute()
    addShutdownHook { process.destroy() }
    process.consumeProcessOutput(out, err)
    process.waitForOrKill(timeout*1000*60*10)
    assert process.exitValue() == 0
}