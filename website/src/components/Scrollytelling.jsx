import React, { useRef } from 'react';
import { motion, useScroll, useTransform } from 'framer-motion';
import InstructionalPlaceholder from './InstructionalPlaceholder';

const FeatureNode = ({ stepNum, title, textPrompt, imagePrompt, reverse }) => {
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
        <InstructionalPlaceholder minHeight="100px" className="p-4 text-xs font-normal bg-transparent">
          {textPrompt}
        </InstructionalPlaceholder>
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
            textPrompt="Provide the exact copywriting text for step 1 explaining video selection and format support."
            imagePrompt="Provide a high-res screenshot of the gallery/selection screen in the app. Replace this div with an <img> tag."
          />
          <FeatureNode 
            stepNum={2}
            title="100% Private AI Analysis"
            textPrompt="Provide copywriting explaining the on-device Whisper model and privacy benefits."
            imagePrompt="Provide a stylized illustration or screenshot showing the transcription loading/processing state. Replace this div."
            reverse
          />
          <FeatureNode 
            stepNum={3}
            title="Style & Export"
            textPrompt="Provide copywriting explaining customization options (colors, fonts) and fast exporting."
            imagePrompt="Provide a screenshot of the Style Editor screen highlighting the color picker. Replace this div."
          />
        </div>
      </div>
    </section>
  );
}
