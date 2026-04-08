/// <reference types="vite/client" />

declare module '*?script' {
    const src: string;
    export default src;
}

declare global {
    interface Window {
        argusInjected?: boolean;
    }
}

export {};
