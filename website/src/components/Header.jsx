import React from 'react';
import InstructionalPlaceholder from './InstructionalPlaceholder';

export default function Header() {
  return (
    <header className="fixed top-0 w-full z-[100] py-4 bg-[#0b0f1999] backdrop-blur-[20px] border-b border-glassBorder">
      <div className="container mx-auto px-8 flex justify-between items-center">
        <a href="#" className="flex items-center gap-3">
          {/* Logo Placeholder */}
          <div className="w-10 h-10 relative">
             <InstructionalPlaceholder minHeight="40px" className="w-10 h-10 p-0 text-[8px] before:hidden flex items-center justify-center rounded-lg border-2">
                Icon
             </InstructionalPlaceholder>
          </div>
          <span className="logo text-2xl font-extrabold text-white hidden sm:block">AutoCaptioner</span>
        </a>
        <nav className="hidden md:flex gap-6 items-center">
          <a href="#features" className="hover:text-accentBlue transition-colors font-medium">How it Works</a>
          <a href="#faq" className="hover:text-accentBlue transition-colors font-medium">FAQ</a>
          <a href="#download" className="btn btn-outline py-2 px-5">Get the App</a>
        </nav>
      </div>
    </header>
  );
}
