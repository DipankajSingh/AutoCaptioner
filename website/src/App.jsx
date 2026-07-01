import React from 'react';
import Header from './components/Header';
import Hero from './components/Hero';
import Scrollytelling from './components/Scrollytelling';
import Testimonials from './components/Testimonials';
import FAQ from './components/FAQ';

function App() {
  return (
    <>
      <div className="absolute w-[600px] h-[600px] rounded-full bg-[radial-gradient(circle,rgba(129,140,248,0.15)_0%,transparent_60%)] blur-[80px] pointer-events-none top-[-20%] left-1/2 -translate-x-1/2 z-0"></div>
      <div className="absolute w-[600px] h-[600px] rounded-full bg-[radial-gradient(circle,rgba(56,189,248,0.1)_0%,transparent_60%)] blur-[80px] pointer-events-none top-[40%] left-[-20%] z-0"></div>
      
      <Header />
      <main>
        <Hero />
        <Scrollytelling />
        <Testimonials />
        <FAQ />
        
        <section className="py-24 border-t border-glassBorder text-center" id="download">
          <div className="container mx-auto px-8 relative z-10">
            <h2 className="text-4xl font-bold mb-4">Start Captioning Today</h2>
            <p className="text-xl text-textSecondary mb-8">Join the closed beta and experience truly private AI.</p>
            <a href="#" className="btn btn-primary text-lg px-8 py-4">Get Started Now</a>
          </div>
        </section>
      </main>
    </>
  );
}

export default App;
