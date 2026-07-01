import React from 'react';
import InstructionalPlaceholder from './InstructionalPlaceholder';

export default function Hero() {
  return (
    <section className="pt-48 pb-24 min-h-screen flex items-center text-center relative" id="home">
      <div className="container mx-auto px-8 flex flex-col items-center gap-16 relative z-10">
        <div className="max-w-[800px]">
          <h1 className="text-5xl md:text-6xl lg:text-7xl font-extrabold leading-[1.05] tracking-tight">
            Video Captions,<br />
            <span className="bg-gradient-to-br from-textPrimary via-textPrimary to-accentBlue bg-clip-text text-transparent">
              Processed On-Device.
            </span>
          </h1>
          <p className="mt-6 mb-10 text-lg md:text-2xl text-textSecondary max-w-[600px] mx-auto">
            Generate perfectly synced, highly accurate captions directly on your Android device. Driven by AI, designed for absolute privacy. No cloud required.
          </p>
          <div>
            <a href="#download" className="btn btn-primary">Download on Google Play</a>
          </div>
        </div>
        
        <div className="w-[300px] md:w-[340px] h-[600px] md:h-[680px] rounded-[44px] bg-black p-[14px] relative shadow-[inset_0_0_0_2px_rgba(255,255,255,0.2),inset_0_0_20px_rgba(255,255,255,0.5),0_25px_50px_-12px_rgba(0,0,0,0.8),0_0_60px_rgba(56,189,248,0.2)] animate-float">
          <div className="w-full h-full rounded-[32px] bg-surface overflow-hidden relative flex flex-col border border-white/10 after:content-[''] after:absolute after:top-0 after:-left-full after:w-1/2 after:h-[200%] after:bg-gradient-to-r after:from-transparent after:via-white/5 after:to-transparent after:-skew-x-[25deg] after:animate-sweep after:pointer-events-none">
            <InstructionalPlaceholder minHeight="100%" className="h-full border-none rounded-none shadow-none text-xs p-4 bg-transparent">
              Create a highly polished, 10-second vertical video (MP4) showcasing the app generating captions on a clip. Crop to 9:16. Replace this entire div with the &lt;video&gt; element.
            </InstructionalPlaceholder>
          </div>
        </div>
      </div>
    </section>
  );
}
