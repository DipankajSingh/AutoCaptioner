"use client";
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
        <h2 className="text-4xl font-bold text-center mb-12">Frequently Asked (And Slightly Panicked) Questions</h2>
        <div>
          <AccordionItem 
            question="Do I need an internet connection to generate captions?" 
            answer="Nope! AutoCaptioner runs OpenAI's Whisper AI directly on your device. Once you've downloaded the language pack, you could literally generate captions from the middle of the Sahara Desert. (Though we question your priorities if you're doing that)." 
          />
          <AccordionItem 
            question="Does this add a watermark to my video?" 
            answer="No, it's 100% watermark-free. You shouldn't have to pay to remove an ugly logo from your own hard work." 
          />
          <AccordionItem 
            question="Can I make captions that look like TikTok or Alex Hormozi videos?" 
            answer="Yes! Our Style Editor lets you create highly engaging word-by-word animations and color pop effects that are currently dominating social media feeds." 
          />
          <AccordionItem 
            question="Are my videos uploaded to the cloud?" 
            answer="Never. We don't want to see your 45 failed attempts at the latest TikTok dance. All processing is strictly local, so your awkward outtakes remain your secret." 
          />
          <AccordionItem 
            question="What languages are supported?" 
            answer="Basically, if humans speak it, we probably have a model for it. You can download various language packs inside the app, turning your phone into a tiny, very fast UN translator." 
          />
          <AccordionItem 
            question="Is it going to melt my phone?" 
            answer="AI is heavy stuff, but our app is incredibly optimized. Your phone might get a little warm if you're transcribing a 3-hour podcast, but it definitely won't melt. We promise." 
          />
        </div>
      </div>
    </section>
  );
}
