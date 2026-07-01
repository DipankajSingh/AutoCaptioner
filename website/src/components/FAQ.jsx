import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import InstructionalPlaceholder from './InstructionalPlaceholder';

const AccordionItem = ({ index }) => {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div className="border-b border-glassBorder overflow-hidden">
      <button 
        onClick={() => setIsOpen(!isOpen)}
        className="w-full py-6 flex justify-between items-center text-left text-xl font-semibold hover:text-accentBlue transition-colors cursor-pointer"
      >
        <InstructionalPlaceholder minHeight="40px" className="p-2 text-xs font-normal flex-1 mr-4 bg-transparent">
          Provide FAQ Question {index} here.
        </InstructionalPlaceholder>
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
            <div className="pb-6">
              <InstructionalPlaceholder minHeight="60px" className="p-4 text-xs font-normal bg-transparent">
                Provide FAQ Answer {index} here.
              </InstructionalPlaceholder>
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
          <AccordionItem index={1} />
          <AccordionItem index={2} />
        </div>
      </div>
    </section>
  );
}
