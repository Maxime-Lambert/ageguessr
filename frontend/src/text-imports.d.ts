// Files imported `with { loader: 'text' }` (Angular's esbuild text loader) arrive as a
// string. Only design-tokens.spec.ts uses it, to read styles.css.
declare module '*.css' {
  const content: string;
  export default content;
}
