"use client";
import React from 'react';

export default function Footer() {
  return (
    <footer className="py-12 border-t border-glassBorder bg-[#080b12]">
      <div className="container mx-auto px-8 flex flex-col md:flex-row justify-between items-center gap-6">
        <div className="flex flex-col items-center md:items-start gap-2">
          <span className="logo text-xl font-extrabold text-white">AutoCaptioner</span>
          <p className="text-sm text-textSecondary">100% Private. 0% Creepy Cloud Storage.</p>
        </div>
        
        <div className="flex gap-6 text-sm text-textSecondary font-medium">
          <a href="/privacy.html" className="hover:text-accentBlue transition-colors">Privacy Policy</a>
          <a href="/terms.html" className="hover:text-accentBlue transition-colors">Terms of Service</a>
          <a href="mailto:support@autocaptioner.com" className="hover:text-accentBlue transition-colors">Contact (Please Be Nice)</a>
        </div>
      </div>
      <div className="container mx-auto px-8 mt-8 flex flex-col md:flex-row justify-between items-center text-xs text-textSecondary/60 gap-4">
        <span>&copy; {new Date().getFullYear()} AutoCaptioner. Built with ☕ and paranoia.</span>
        <span className="font-semibold text-textSecondary/80">A product of DipDev Labs</span>
      </div>
    </footer>
  );
}
