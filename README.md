# Headcontrol, eine interaktive Mod für Minecraft.

## Übersicht über die Beweggründe und Funktion der Mod
Unser Ziel war es den Spielern von Minecraft einen neuen Weg zu geben um das Spiel zu erleben, dafür haben wir diese Mod erstellt. Sie macht es möglich die eigene Spielfigur über die Bewegungen des Kopfes zu steuern. Dabei kann man die in-Game Blickrichtung über die eigene Blickrichtung steuern. Lehnen des Oberkörpers nach vorne oder hinten macht es möglich nach vorne oder hinten zu laufen, sowie das neigen des Kopfes nach links und rechts es ermöglicht nach links oder rechts zu laufen.

## AMITUDE Modell
(A): Eine Minecraft Anwendung (T): in welcher man die Welt erkundet, baut und abbaut (E): auf einem Desktop Computer (I):((U) in dem die Benutzer (M) mit Kopfbewegungen Befehle ausführen welche, (D): über eine Webcam aufgenommen werden (M): um Interaktionen im Spiel umzusetzen.

## CROW Modell
| Dimension | Beschreibung |
|:---------:|:-------------|
|Character | Unsere Spieler sind vor allem Menschen, die Minecraft auf ihrem PC oder Laptop spielen. Dabei haben sie im optimalen Fall bereits Erfahrung mit der Steuerung in Minecraft und dem Spiel selbst. Sie suchen eine intuitivere Möglichkeit die Bewegung ihrer Spielfigur zu kontrollieren, oder suchen eine neue Herausforderung. Sie wollen Minecraft auf eine frische, ungewohnte Weise erleben und entdecken. |
|Relationship | Die Nutzer sind im größten Teil unerfahren in der Nutzung bewegungsbasierter Steuerungsarten. Deshalb brauchen sie klare, leicht verständliche Anleitungen und eine Mod, die ihnen eine angenehme Lernkurve bietet. Des Weiteren ist ihnen eine simple und intuitive Integration der Mod in Minecraft wichtig. |
|Object | Unsere Spieler möchten ein unterhaltsames Erlebnis was auf Minecraft aufbaut und dieses um eine neue Steuerungsmöglichkeit erweitert. Die Bewegungen sollen schnell und zuverlässig im Spiel ankommen, damit sich die Steuerung natürlich anfühlt und kein Unwohlsein durch Verzögerungen entsteht. Dabei ist es auch wichtig das Totzonen - in denen die Mod nicht auf Bewegungen reagiert, verwendet werden um eine zu steife, unnatürliche und unangenehme Körperhaltung zu vermeiden. |
|Where | Genutzt wird die Mod hauptsächlich zu Hause, mit genügend Platz um simple Bewegungen wie Vor- und Zurückrücklehnen oder das Neigen nach links und rechts zu ermöglichen. Wichtig ist ein stabiler Untergrund, wie z.B.: ein Schreibtisch, um die Kamera zu platzieren, sowie gute Lichtverhältnisse um die Möglichkeit von Fehlerkennungen und Eingaben soweit wie möglich zu reduzieren und das Spielerlebnis so reibungslos wie möglich zu gestallten. 


## Storyboard
![Storyboard](./documentation/img/storyboard_05.png)

## Start und Installationshinweise

**Disclaimer:** Ursprünglich wollten wir die Mod für einfaches herunterladen und ausführen über den bekannte Minecraft-Mod Loader Curseforge veröffentlichen, da es dabei noch unerwartete Probleme gibt ist es aktuell nur über eine IDE wie Eclipse oder IntelliJ möglich.

Anleitung unter InteliJ und Eclipse(Mit Gradle-Plugin)

Es wird InteliJ oder Eclipse und eine Java JDK mit mindestens Java21 benötigt.
Nach Öffnen des Projekts in der IDE muss das Gradle Projekt gebaut werden, das kann mehrere Minuten dauern. 
Wenn das abgeschlossen ist kann man in IntelliJ auf der rechten Seite, oder in Eclipse unten in einem Tab neben dem Terminal, im Gradle Tab unter Headcontrol -> Tasks -> forgegradle runs auf "runClient" gedoppelklickt werden. 
Dannach sollte sich eine Minecraft Version 21.1 Instanz öffnen die die Mod installiert hat.

Wenn man eine Singleplayer Welt gestartet hat, einfach den auf "B" vordefinierten Hotkey drücken um OpenCV einmalig zu initialisiern und zu starten.
Man kann über das Menü in-Game die wichtigsten Einstellungen vornehmen. Man öffnet es in dem man den Hotkey "N" drückt.
