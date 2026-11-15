import { DOCUMENT } from '@angular/common';
import { InjectionToken, inject } from '@angular/core';

/** Hands a file to the browser to save, behind a token so the specs never download anything. */
export interface FileSaver {
  save(file: Blob, filename: string): void;
}

export const FILE_SAVER = new InjectionToken<FileSaver>('FileSaver', {
  factory: () => {
    const document = inject(DOCUMENT);
    return {
      save: (file, filename) => {
        // A link to the file in memory, clicked then revoked: the usual way to save a
        // file the page fetched itself (with its bearer token, which a plain link lacks).
        const url = URL.createObjectURL(file);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        link.click();
        URL.revokeObjectURL(url);
      },
    };
  },
});

/** The name the server gave the file (Content-Disposition), else a fallback. */
export function filenameOf(contentDisposition: string | null, fallback: string): string {
  return contentDisposition?.match(/filename="?([^";]+)"?/)?.[1] ?? fallback;
}
