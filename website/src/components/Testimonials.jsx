import React from 'react';
import { motion } from 'framer-motion';
import InstructionalPlaceholder from './InstructionalPlaceholder';

export default function Testimonials() {
  const cards = Array(3).fill(null).map((_, i) => (
    <div key={i} className="w-[350px] p-8 bg-surface border border-glassBorder rounded-2xl flex-shrink-0 whitespace-normal">
      <InstructionalPlaceholder minHeight="150px" className="p-4 text-xs font-normal bg-transparent">
        Provide Testimonial {i + 1} (Quote, Name, Avatar image).
      </InstructionalPlaceholder>
    </div>
  ));

  return (
    <section className="py-24">
      <div className="container mx-auto px-8 text-center mb-12">
        <h2 className="text-4xl font-bold">Loved by Beta Testers</h2>
      </div>
      <div className="overflow-hidden whitespace-nowrap relative py-4 flex">
        <div className="absolute top-0 left-0 w-[150px] h-full bg-gradient-to-r from-deepSpace to-transparent z-10"></div>
        <div className="absolute top-0 right-0 w-[150px] h-full bg-gradient-to-l from-deepSpace to-transparent z-10"></div>
        
        <motion.div 
          className="inline-flex gap-8 flex-nowrap shrink-0"
          animate={{ x: ['0%', '-50%'] }}
          transition={{ repeat: Infinity, duration: 20, ease: 'linear' }}
          style={{ width: 'max-content' }}
        >
          <div className="flex gap-8 shrink-0">{cards}</div>
          <div className="flex gap-8 shrink-0 pl-8">{cards}</div>
        </motion.div>
      </div>
    </section>
  );
}
