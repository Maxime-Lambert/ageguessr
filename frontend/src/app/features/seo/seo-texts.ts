/**
 * What search engines and link previews read about the site. Used twice: by `Seo` at run
 * time (Google runs the JavaScript), and by `scripts/seo-postbuild.mjs`, which reads the
 * same ids from the XLIFF files to write them into each language's index.html for the
 * previews that never run it (WhatsApp, Discord, X…). Keep the ids in step with that script.
 */
export const SEO_TEXT = {
  /** The title of a link preview: the site and what it is, in a few words. */
  title: $localize`:@@seo.title:AgeGuessr: guess the age in the photo`,
  /** The site's description: the home page, and every page without its own. */
  description: $localize`:@@seo.description:A daily game: guess how old the person in an archive photo was, helped by facts from history, music, film and more. Three photos a day, free, no account needed.`,
  free: $localize`:@@seo.description.free:Play as many series as you like: guess the age of people in archive photos, beyond the daily game. Free, no account needed.`,
  howToPlay: $localize`:@@seo.description.howToPlay:How to play AgeGuessr: two guesses per photo, hints to narrow down the age, and points by how close you get.`,
  legalNotice: $localize`:@@seo.description.legalNotice:Legal notice of AgeGuessr: publisher and host.`,
  terms: $localize`:@@seo.description.terms:Terms of Use of AgeGuessr.`,
  privacy: $localize`:@@seo.description.privacy:Privacy Policy of AgeGuessr: the data kept, why, for how long, and your rights.`,
};
