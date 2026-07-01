import React from 'react';

export default function Header() {
  return (
    <header className="fixed top-0 w-full z-[100] py-4 bg-[#0b0f1999] backdrop-blur-[20px] border-b border-glassBorder">
      <div className="container mx-auto px-8 flex justify-between items-center">
        <a href="#" className="logo text-2xl font-extrabold text-white">AutoCaptioner</a>
        <nav className="hidden md:flex gap-6 items-center">
          <a href="#features" className="hover:text-accentBlue">How it Works</a>
          <a href="#faq" className="hover:text-accentBlue">FAQ</a>
          <a href="#download" className="btn btn-outline">Get the App</a>
        </nav>
      </div>
    </header>
  );
}
