// Files imported `with { loader: 'text' }` (Angular's esbuild text loader) arrive as a
// string. Only design-tokens.spec.ts uses it, to read styles.css. Excluded from
// tsconfig.app.json: app code importing a .css file must not be typed as a string.
declare module '*.css' {
  const content: string;
  export default content;
}
