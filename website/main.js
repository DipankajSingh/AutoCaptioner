// main.js

document.addEventListener('DOMContentLoaded', () => {
  // Inject environment variables
  const playStoreLink = import.meta.env.VITE_PLAY_STORE_LINK;
  const contactEmail = import.meta.env.VITE_CONTACT_EMAIL;

  const heroCtaBtn = document.getElementById('hero-cta-btn');
  const navCtaBtn = document.getElementById('nav-cta');
  const footerCtaBtn = document.getElementById('footer-cta-btn');
  const footerContactLink = document.getElementById('footer-contact-link');

  if (heroCtaBtn) heroCtaBtn.href = playStoreLink;
  if (navCtaBtn) navCtaBtn.href = playStoreLink;
  if (footerCtaBtn) footerCtaBtn.href = playStoreLink;

  if (footerContactLink) {
    footerContactLink.href = `mailto:${contactEmail}`;
    footerContactLink.textContent = contactEmail;
  }

  // Smooth scroll for anchor links
  document.querySelectorAll('a[href^="#"]').forEach(anchor => {
    anchor.addEventListener('click', function (e) {
      const targetId = this.getAttribute('href');
      if(targetId === '#') return;
      const targetElement = document.querySelector(targetId);
      if(targetElement) {
        e.preventDefault();
        targetElement.scrollIntoView({
          behavior: 'smooth'
        });
      }
    });
  });

  // Simple micro-animation for the device mockup caption words
  const captionWords = document.querySelectorAll('.caption-word');
  if (captionWords.length > 0) {
    let currentIndex = 0;
    setInterval(() => {
      // Remove active class from current word
      captionWords[currentIndex].classList.remove('active');
      
      // Move to next word
      currentIndex = (currentIndex + 1) % captionWords.length;
      
      // Add active class to new word
      captionWords[currentIndex].classList.add('active');
    }, 800); // Change word every 800ms
  }
});
