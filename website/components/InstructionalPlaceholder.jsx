"use client";
import React from 'react';

export default function InstructionalPlaceholder({ children, className = '', minHeight = '200px' }) {
  return (
    <div 
      className={`border-[3px] border-dashed border-warningYellow bg-warningYellowBg text-warningYellow p-4 md:p-8 text-center font-mono font-bold text-sm md:text-base rounded-xl w-full flex flex-col justify-center items-center gap-4 shadow-[0_0_20px_rgba(255,230,0,0.2)] backdrop-blur-sm ${className}`}
      style={{ minHeight }}
    >
      <span className="before:content-['⚠️_ACTION_REQUIRED_⚠️'] before:text-lg md:before:text-[1.4rem] before:animate-blink"></span>
      {children}
    </div>
  );
}
