## Hinweise zum aktuellen Stand und starten der Demo

Da ich aktuell noch in der Entwicklung der Demo bin habe ich sie noch nicht in eine .jar für Minecraft kompiliert, und muss aus der IDE herraus gestartet werden.

Anleitung unter InteliJ:

Es wird InteliJ oder Eclipse und eine Java JDK mit mindestens Java21 benötigt.
Nach Öffnen des Projekts in der IDE muss das Gradle Projekt gebaut werden, das kann mehrere Minuten dauern. 
Wenn das abgeschlossen ist kann man auf der rechten Seite im Gradle Tab unter MMMIGr12 -> Tasks -> forgegradle runs auf "runClient" gedoppelklickt werden. 
Dannach sollte sich eine Minecraft Version 21.1 Instanz öffnen die die Mod installiert hat.

Wenn man eine Singleplayer Welt gestartet hat, einfach den auf "B" vordefinierten Hotkey drücken um OpenCV zu starten. Im Fenster von OpenCV dann eine Kamera auswählen 
und "start camera" klicken. Dannach den Bildschirmanweisungen folgen, nach abgeschlossener Kalibrierung kann man seinen in-Game Charakter nun per Bewegungen steuern.

Man kann auch inGame die Kameraeinstellungen vornehmen in dem man den Hotkey "N" drückt. Doch damit das funktioniert muss man Anfangs einmal den Hotkey "B" drücken damit OpenCV überhaut gestartet ist. 
