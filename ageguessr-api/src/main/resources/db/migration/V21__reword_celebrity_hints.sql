-- Celebrites reformulees (feature/better-hints-gameplay) : « Personnalite nee cette
-- annee-la : X, desc. » laissait le joueur faire le calcul. L'indice dit maintenant
-- directement l'age : celui qu'a X cette annee. Il est exact, puisque l'annee de l'indice
-- est l'annee de la partie (ou de la serie) moins l'age sur la photo.
--
-- Seul le debut du texte change : rien ne separe surement le nom de la description (qui
-- contient elle-meme des virgules). Le meme texte est dans celebrities.json et
-- extract_wikidata.py, sinon les prochaines cles importees reviendraient a l'ancien.
-- Idempotent : une ligne deja reecrite, ou corrigee a la main, ne commence plus par
-- l'ancien prefixe.

UPDATE hints
SET text_fr = 'Sur la photo, cette personne a l''âge qu''a cette année '
        || substr(text_fr, char_length('Personnalité née cette année-là : ') + 1)
WHERE category = 'CELEBRITIES'
  AND starts_with(text_fr, 'Personnalité née cette année-là : ');

UPDATE hints
SET text_en = 'On the photo, this person is as old as this celebrity turns this year: '
        || substr(text_en, char_length('Born that year: ') + 1)
WHERE category = 'CELEBRITIES'
  AND starts_with(text_en, 'Born that year: ');
