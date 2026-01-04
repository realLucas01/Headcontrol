
um das Programm zu starten:   
**InteliJ:**   
run configurations  
"+" Maven  
in Run einfügen:   clean javafx:run  


**Eclipse:**  
Run Configuration  
Arguments  
VM Arguments  
--module-path "C:\path\to\javafx\lib" --add-modules javafx.controls,javafx.fxml,javafx.swing  

oder über Befehl:  
mvn clean javafx:run   

Die Dateien: Main, utils.java, FirstFX.fxml stammen nicht von mir und stammen größtenteils von https://github.com/opencv-java/camera-calibration/tree/master  
Im Controller können derzeit mehrere unnötige oder fehlerhafte code blöcke existieren, da die Datei mehrfach zum testen grundlegend überarbeitet wurde. 
