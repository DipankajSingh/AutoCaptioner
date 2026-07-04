import React, { useRef } from 'react';
import { motion, useScroll, useTransform } from 'framer-motion';
import InstructionalPlaceholder from './InstructionalPlaceholder';

const FeatureNode = ({ stepNum, title, text, imagePrompt, reverse }) => {
  return (
    <motion.div 
      initial={{ opacity: 0.2, y: 30 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: false, amount: 0.5 }}
      transition={{ duration: 0.5 }}
      className={`flex flex-col md:flex-row items-center justify-between w-full gap-8 ${reverse ? 'md:flex-row-reverse' : ''}`}
    >
      <div className="w-full md:w-[45%]">
        <h2 className="text-3xl font-bold mb-4">{stepNum}. {title}</h2>
        <p className="text-lg text-textSecondary leading-relaxed bg-transparent">
          {text}
        </p>
      </div>
      <div className="w-full md:w-[45%] bg-glassSurface border border-glassBorder rounded-3xl p-6 relative overflow-hidden backdrop-blur-md">
        <div className="absolute inset-0 rounded-3xl p-[1px] bg-gradient-to-br from-white/10 to-transparent pointer-events-none" style={{ mask: 'linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0)', maskComposite: 'exclude' }}></div>
        <InstructionalPlaceholder minHeight="200px" className="p-4 text-xs">
          {imagePrompt}
        </InstructionalPlaceholder>
      </div>
    </motion.div>
  );
};

export default function Scrollytelling() {
  const containerRef = useRef(null);
  const { scrollYProgress } = useScroll({
    target: containerRef,
    offset: ["start center", "end center"]
  });

  const scaleY = useTransform(scrollYProgress, [0, 1], [0, 1]);

  return (
    <section className="py-32 relative" id="features" ref={containerRef}>
      <div className="container mx-auto px-8 relative">
        <div className="hidden md:block absolute top-0 bottom-0 left-1/2 w-[2px] bg-glassBorder -translate-x-1/2 z-0">
          <motion.div 
            className="absolute top-0 left-0 w-full bg-gradient-to-b from-accentCyan to-accentViolet shadow-[0_0_15px_#22d3ee] origin-top"
            style={{ scaleY, height: '100%' }}
          />
        </div>

        <div className="flex flex-col gap-32 relative z-10">
          <FeatureNode 
            stepNum={1}
            title="Select Your Video"
            text="Pick any video from your gallery. Whether it's a cinematic masterpiece or a chaotic 3AM rant about pizza, AutoCaptioner imports it instantly without uploading a single byte to the internet."
            imagePrompt="Provide a high-res screenshot of the gallery/selection screen in the app. Replace this div with an <img> tag."
          />
          <FeatureNode 
            stepNum={2}
            title="Instant, Accurate Auto Captions"
            text="Our on-device Whisper models listen to your audio and transcribe it automatically with terrifying accuracy. No need to upload massive video files to the cloud—it all happens instantly on your phone."
            imagePrompt="Provide a stylized illustration or screenshot showing the transcription loading/processing state (model manager). Replace this div."
            reverse
          />
          <FeatureNode 
            stepNum={3}
            title="Make it Pop (TikTok Style)"
            text="Adjust colors, fonts, and positioning to get that viral 'Alex Hormozi' look. Create dynamic, color-changing text that highlights words as you speak, and export it straight to your camera roll with absolutely zero watermarks."
            imagePrompt="Provide a screenshot of the Style Editor screen highlighting the color picker. Replace this div."
          />
        </div>
      </div>
    </section>
  );
}
