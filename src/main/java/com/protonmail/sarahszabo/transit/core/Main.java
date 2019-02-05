/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.protonmail.sarahszabo.transit.core;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.stage.DirectoryChooser;

/**
 * The main class of the transit program. This program takes all .vob files in a
 * folder and writes them to another "finished" folder as .mp4.
 *
 * @author Sarah Szabo <SarahSzabo@Protonmail.com>
 */
public class Main {

    private static BlockingQueue<Path> queue = new ArrayBlockingQueue<>(1);
    private static int counter = 0;

    /**
     * @param args the command line arguments
     * @throws java.lang.InterruptedException
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws InterruptedException, IOException {
        //Assumes that we have a finished folder as arg1 and we'll use a file chooser for folder 2
        if (args.length != 1) {
            System.err.println("Arg length not correct. Specify the output folder as the only argument");
            System.exit(-1);
        }
        //Initialize JavaFX Toolkit
        new JFXPanel();
        //Get folder to convert
        Platform.runLater(() -> {
            try {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Transit: Choose the Root Directory for Conversion");
                queue.put(chooser.showDialog(null).toPath());
            } catch (InterruptedException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        Path folderPath = queue.take();
        //We're converting from .vob to .mp4, visit all files
        var paths = Files.walk(folderPath).parallel();
        paths.filter(path -> !Files.isDirectory(path) && path.getFileName().toString().endsWith(".VOB")).forEach(path -> {
            //Netbeans being annoying, technically pointless, but this is a workaround
            Path filePath = path;
            ////Get Filename From MEA to VIDEO_TS
            int start = 0, end = 0;
            for (int i = 0; i < filePath.getNameCount(); i++) {
                Path part = filePath.getName(i);
                if (part.startsWith("MEA")) {
                    start = i;
                } else if (part.startsWith("VIDEO_TS")) {
                    end = i;
                }
            }
            if (end == 0) {
                end = filePath.getNameCount() - 1;
            }
            //Did we get both?
            if (start == 0) {
                throw new IllegalStateException("Start not defined");
            }
            //Good, continue and get name from start to end
            String fileName = filePath.subpath(start + 1, end).toString().replace(FileSystems.getDefault().getSeparator(), " -- ")
                    + " " + counter;
            System.out.println(fileName);
            try {//https://github.com/SarahSzabo/Transit.git
                //Ok, now submit the conversion task and wait for conversations to finish
                //ffmpeg -i VTS_01_1.VOB -c:v libx264 -c:a aac -strict experimental Test.mp4
                ProcessBuilder builder = new ProcessBuilder("ffmpeg", "-i", filePath.getFileName().toString(),
                        "-c:v", "libx264", "-c:a", "aac", "-strict", "experimental",
                        args[0] + "/" + fileName + ".mp4")
                        .directory(filePath.getParent().toFile()).inheritIO();
                Process proc = builder.start();
                proc.waitFor();
                System.out.println("\n\n");
                builder.command().stream().forEachOrdered(string -> System.out.print(string + " "));

            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            }
            counter++;
        });
        System.exit(0);
    }

}
