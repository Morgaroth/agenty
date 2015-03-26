1. Wg naszych obserwacji Jade jest biblioteką, która 
  * zapewnia uruchamianie klas javy:Agentów, którzy mogą implementować pewną logikę
  * zapewnia komunikację pomiędzy nimi
  * ewentualnie zarządza ich rozproszeniem na różne komputery
  * przy czym rozproszenie obliczeń nie jest główną cechą systemów agentowych
  * a fakt, że budują ją względnie samodzielne jednostki


2. Analiza źródeł danych
  * właściwie z początku odrzuciliśmy portale polskojęzyczne ze względu na małą grupę twórców
  * postanowiliśmy skupić się na portalach anglojęzycznych:
  * nie skupialiśmy się na małych portalach z powodu małego ruchu na nich i słabej pomocy do pracy z nimi
    * mało osób używających
    * słabo udokumentowane API lub jego brak
  * pozostało kilka największych, ze względu na dużą ilość treści produkowanej przez użytkowników oraz popularność skutkującą również większym wspraciem dla współpracy z API -> obszerniejsza dokumentacja oraz lista znanych problemów wraz z rozwiązaniami w Internecie
    * Facebook
    * Twitter
  * proponujemy skupić się na Twitterze na początek
    * ze wzlgędu na ogrom treści
    * może być dobry do symulowania powiązań między użytkownikami
  * facebook w drugiej kolejności


3. Analiza technologii:
  * w kontekście wniosków w punktu pierwszego pomyśleliśmy, że:
    * jade w pewnym sensie jest biblioteką obsługującą równoległe i samodzielne działanie wykonywalnych jednostek
    * te jednostki mają własną logikę działania, jednakże w większości (lub całkowicie) trzeba ją zaimplementować
    * jade również obsługuje lokalizację się agentów między sobą pozwalając im 
      * wyszukiwać się nawzajem 
      * przesyłać sobie wiadomości (przez nazwę adresata), lub bardziej adresować wiadomość do adresata
  * pomysł technologii:
    * Akka -> biblioteka framework realizujący podobne aspekty jak przytoczone powyżej:
      * zapewnia równoległe wykonania jednostek, zwanych Aktorami
      * implementuje model Aktorów, znany z Erlanga
      * realizuje elementy konceptu Agentów:
        * glównym elementem wykonania programu są względnie niezależne jednostki
          * nazywane aktorami
          * posiadające własny stan
          * posiadające własną logikę działania
        * współpracują poprzez wymianę komunikatów
        * zapewnia współpracę w środowisku rozproszonym (TCP pomiędzy instacjami Akki)
    * Scala
      * język obiektowo funkcyjny
      * kompilowany do ByteCode JVM -> uruchamiany na JVM
      * integrujący się z Javą w 98%
        * użycie bibliotek Javy w Scali -> 99.9%
        * użycie bibliotek Scali w Javie -> 70%
      * popularny ostatnimi czasy, prężnie się rozwijający
      * rosnąca liczba narzędzi


4. Propozycja rozwiązania:
  * na początek
    * ustawienie systemu oraz konfiguracja
    * implementacja komunikacji z twitterem
    * prosta logika aktora polegająca na prostej analizie twitów
      * np statystyka słów
      * np statystyka hasz tagów
  * rozbudowa
    * analiza powiązań:
      * lista śledzonych / obserwowanych
      * lista śledzących / obserwujących
      * analza hashtagów: ich statystyka wśród znajomych / własnych
      * powiązywanie kontaktów przez wspominanie innych w tłitach
