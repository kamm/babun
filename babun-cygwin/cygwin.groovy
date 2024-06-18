#!/usr/bin/env groovy

//"C:\exp\babun\setup-x86_64.exe" -O -s https://rpi.kmetrak.eu/pub/cygnus/cygwin --quiet-mode --root "C:\exp\babun\target\babun-cygwin\cygwin" --no-shortcuts --no-startmenu --no-desktop --packages alternatives,attr,automake,base-cygwin,base-files,bash,bc,bzip2,chere,coreutils,cron,crypt,csih,ctags,curl,cygutils,cygwin,dash,diffutils,dos2unix,editrights,findutils,fzf,gawk,gcc-core,gettext,git,gnutls,grep,groff,gzip,hostname,ipc-utils,keychain,less,libsasl2_3,links,login,make,man,mc,mintty,nano,nc,ncurses,openssh,openssl,p7zip,pcre,perl,perl_vendor,ping,pkg-config,procps,readline,rebase,rsync,run,sed,shutdown,tar,terminfo,time,tmux,tree,util-linux,unzip,vim,wget,which,xorg-server,xinit,zip,zsh

import static java.lang.System.*

execute()

def execute() {
    File repoFolder, inputFolder, outputFolder, cygwinFolder, pkgsFile
    boolean downloadOnly
    try {
        checkArguments()
        (repoFolder, inputFolder, outputFolder, cygwinFolder, pkgsFile, downloadOnly) = initEnvironment()
        // install cygwin
        File cygwinInstaller = downloadCygwinInstaller(outputFolder)
        if(downloadOnly) {
            println "downloadOnly flag set to true - Cygwin installation skipped.";
            return
        }
        installCygwin(cygwinInstaller, repoFolder, cygwinFolder, pkgsFile)
        cygwinInstaller.delete()

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

def checkArguments() {
    if (this.args.length != 5) {
        error("Usage: cygwin.groovy <repo_folder> <input_folder> <output_folder> <pkgs_file> <download_only>")
        exit(-1)
    }
}

def initEnvironment() {
    File repoFolder = new File(this.args[0])
    File inputFolder = new File(this.args[1])
    File outputFolder = new File(this.args[2])
    File pkgsFile = new File(this.args[3]) 
    boolean downloadOnly =  Boolean.parseBoolean(this.args[4])
    if (!outputFolder.exists()) {
        outputFolder.mkdir()
    }    
    File cygwinFolder = new File(outputFolder, "cygwin")
    cygwinFolder.mkdir()
    return [repoFolder, inputFolder, outputFolder, cygwinFolder, pkgsFile, downloadOnly]
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
            "--local-install " +
            "--local-package-dir \"${repoFolder.absolutePath}\" " +
            "--root \"${cygwinFolder.absolutePath}\" " +
            "--no-shortcuts " +
            "--no-startmenu " +
            "--no-desktop " +
            "--packages " + pkgs
    err.println installCommand
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
    process.consumeProcessOutput(out, err)
    process.waitForProcessOutput()
    return process.exitValue()
}

def error(String message, boolean noPrefix = false) {
    err.println((noPrefix ? "" : "ERROR: ") + message)
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
