import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

const AccordionItem = ({ question, answer }) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="border-b border-glassBorder overflow-hidden">
      <button 
        onClick={() => setIsOpen(!isOpen)}
        className="w-full py-6 flex justify-between items-center text-left text-xl font-semibold hover:text-accentBlue transition-colors cursor-pointer"
      >
        <span className="flex-1 mr-4">{question}</span>
        <span className="text-2xl">{isOpen ? '-' : '+'}</span>
      </button>
      <AnimatePresence>
        {isOpen && (
          <motion.div 
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            className="overflow-hidden"
          >
            <div className="pb-6 text-textSecondary text-lg leading-relaxed">
              {answer}
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

export default function FAQ() {
  return (
    <section className="py-32" id="faq">
      <div className="container mx-auto px-8 max-w-[800px]">
        <h2 className="text-4xl font-bold text-center mb-12">Frequently Asked Questions</h2>
        <div>
          <AccordionItem 
            question="Do I need an internet connection to generate captions?" 
            answer="No! AutoCaptioner runs OpenAI's Whisper AI models directly on your device. Once the app and language models are downloaded, you can generate highly accurate captions completely offline." 
          />
          <AccordionItem 
            question="Are my videos uploaded to the cloud?" 
            answer="Never. Because all processing is done locally via on-device machine learning, your videos and audio never leave your phone. Your data remains 100% private." 
          />
          <AccordionItem 
            question="What languages are supported?" 
            answer="AutoCaptioner supports multiple languages through downloadable Whisper models. You can easily manage and download the specific language packs you need right inside the app." 
          />
          <AccordionItem 
            question="Can I customize the look of the captions?" 
            answer="Yes. The app includes a built-in style editor that allows you to change fonts, text colors, background colors, and the positioning of the captions on the video." 
          />
        </div>
      </div>
    </section>
  );
}
